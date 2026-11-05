package com.ecom.cartora.payment;

import com.ecom.cartora.exception.PaymentCannotBeDone;
import com.ecom.cartora.exception.OrderNotFoundException;
import com.ecom.cartora.order.entity.Order;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.repository.OrderRepo;
import com.ecom.cartora.payment.dto.RefundRequest;
import com.ecom.cartora.payment.dto.RefundResponse;
import com.ecom.cartora.payment.entity.Payment;
import com.ecom.cartora.payment.entity.PaymentRefund;
import com.ecom.cartora.payment.entity.PaymentStatus;
import com.ecom.cartora.payment.entity.RefundStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RefundService {

    private final RazorpayProperties properties;
    private final RestClient razorpayRestClient;
    private final PaymentRepo paymentRepo;
    private final PaymentRefundRepo refundRepo;
    private final OrderRepo orderRepo;

    public RefundService(
            RazorpayProperties properties,
            RestClient razorpayRestClient,
            PaymentRepo paymentRepo,
            PaymentRefundRepo refundRepo,
            OrderRepo orderRepo) {
        this.properties = properties;
        this.razorpayRestClient = razorpayRestClient;
        this.paymentRepo = paymentRepo;
        this.refundRepo = refundRepo;
        this.orderRepo = orderRepo;
    }

    @Transactional
    public RefundResponse refundPayment(Long paymentId, RefundRequest request) {
        requireConfigured();
        Payment initial = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        Order order = orderRepo.findByIdForUpdate(initial.getOrder().getId())
                .orElseThrow(() -> new OrderNotFoundException("Order doesn't exist"));
        Payment payment = paymentRepo.findByOrderIdForUpdate(order.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (!payment.getId().equals(paymentId)
                || payment.getStatus() == PaymentStatus.FAILED
                || payment.getStatus() == PaymentStatus.PENDING
                || payment.getProviderPaymentId() == null) {
            throw new PaymentCannotBeDone("Only captured payments can be refunded");
        }
        return issueRefund(payment, request == null ? null : request.getAmount());
    }

    @Transactional
    public RefundResponse refundLateCapture(Payment payment, BigDecimal amount) {
        return issueRefund(payment, amount);
    }

    @Transactional
    public void updateRefundStatus(String providerRefundId, boolean processed) {
        PaymentRefund refund = refundRepo.findByProviderRefundId(providerRefundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund was not found"));
        Long orderId = refund.getPayment().getOrder().getId();
        orderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order doesn't exist"));
        Payment payment = paymentRepo.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        refund = refundRepo.findByProviderRefundId(providerRefundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund was not found"));
        if (refund.getStatus() != RefundStatus.PENDING && refund.getStatus() != RefundStatus.REQUESTED) return;
        if (!payment.getId().equals(refund.getPayment().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund payment changed while processing");
        }
        refund.setStatus(processed ? RefundStatus.PROCESSED : RefundStatus.FAILED);
        refund.setUpdatedAt(LocalDateTime.now());
        refundRepo.save(refund);
        refreshPaymentRefundStatus(payment);
    }

    private RefundResponse issueRefund(Payment payment, BigDecimal requestedAmount) {
        List<PaymentRefund> refunds = refundRepo.findByPaymentIdOrderByCreatedAtDescForUpdate(payment.getId());
        PaymentRefund retryable = refunds.stream()
                .filter(refund -> refund.getStatus() == RefundStatus.REQUESTED)
                .findFirst().orElse(null);
        if (retryable != null) {
            if (requestedAmount != null && requestedAmount.compareTo(retryable.getAmount()) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A refund request is awaiting provider confirmation; retry it with the same amount");
            }
            return submitRefund(payment, retryable);
        }
        BigDecimal alreadyRefundedOrPending = refunds.stream()
                .filter(refund -> refund.getStatus() != RefundStatus.FAILED)
                .map(PaymentRefund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = payment.getAmount().subtract(alreadyRefundedOrPending);
        BigDecimal amount = requestedAmount == null ? remaining : requestedAmount;
        if (amount.signum() <= 0 || amount.compareTo(remaining) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount exceeds the remaining refundable amount");
        }
        if (amount.scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount must have at most two decimal places");
        }

        try {
            amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount must have at most two decimal places");
        }
        LocalDateTime now = LocalDateTime.now();
        PaymentRefund refund = new PaymentRefund();
        String idempotencyKey = UUID.randomUUID().toString();
        refund.setPayment(payment);
        refund.setReceipt("rf_" + idempotencyKey.replace("-", ""));
        refund.setIdempotencyKey(idempotencyKey);
        refund.setAmount(amount);
        refund.setStatus(RefundStatus.REQUESTED);
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);
        refund = refundRepo.saveAndFlush(refund);
        payment.setStatus(PaymentStatus.REFUND_PENDING);
        payment.setUpdatedAt(now);
        paymentRepo.save(payment);
        return submitRefund(payment, refund);
    }

    private RefundResponse submitRefund(Payment payment, PaymentRefund refund) {
        long minorAmount = refund.getAmount().movePointRight(2).longValueExact();
        JsonNode result;
        try {
            result = razorpayRestClient.post()
                    .uri("/payments/{paymentId}/refund", payment.getProviderPaymentId())
                    .headers(headers -> {
                        headers.setBasicAuth(properties.getKeyId(), properties.getKeySecret());
                        headers.set("X-Refund-Idempotency", refund.getIdempotencyKey());
                    })
                    .body(java.util.Map.of("amount", minorAmount, "receipt", refund.getReceipt()))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            // Keep the durable intent so an admin retry uses the same provider idempotency key.
            return toResponse(payment, refund);
        }
        if (result == null || result.path("id").asText().isBlank()) {
            return toResponse(payment, refund);
        }

        refund.setProviderRefundId(result.path("id").asText());
        String status = result.path("status").asText("pending");
        refund.setStatus("processed".equalsIgnoreCase(status) ? RefundStatus.PROCESSED : RefundStatus.PENDING);
        refund.setUpdatedAt(LocalDateTime.now());
        refundRepo.save(refund);
        refreshPaymentRefundStatus(payment);
        return toResponse(payment, refund);
    }

    private RefundResponse toResponse(Payment payment, PaymentRefund refund) {
        return new RefundResponse(refund.getId(), payment.getId(), payment.getOrder().getId(),
                refund.getProviderRefundId(), refund.getReceipt(), refund.getAmount(),
                refund.getStatus(), refund.getCreatedAt());
    }

    private void refreshPaymentRefundStatus(Payment payment) {
        List<PaymentRefund> refunds = refundRepo.findByPaymentIdOrderByCreatedAtDesc(payment.getId());
        BigDecimal processed = refunds.stream()
                .filter(refund -> refund.getStatus() == RefundStatus.PROCESSED)
                .map(PaymentRefund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal committed = refunds.stream()
                .filter(refund -> refund.getStatus() != RefundStatus.FAILED)
                .map(PaymentRefund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean pending = refunds.stream().anyMatch(refund -> refund.getStatus() == RefundStatus.PENDING
                || refund.getStatus() == RefundStatus.REQUESTED);
        LocalDateTime now = LocalDateTime.now();
        if (committed.compareTo(payment.getAmount()) >= 0
                && processed.compareTo(payment.getAmount()) >= 0) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.getOrder().setStatus(OrderStatus.REFUNDED);
            payment.getOrder().setUpdatedAt(now);
            orderRepo.save(payment.getOrder());
        } else if (pending) {
            payment.setStatus(PaymentStatus.REFUND_PENDING);
        } else if (processed.signum() > 0) {
            payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        } else {
            payment.setStatus(PaymentStatus.SUCCESS);
        }
        payment.setUpdatedAt(now);
        paymentRepo.save(payment);
    }

    private void requireConfigured() {
        if (properties.getKeyId() == null || properties.getKeyId().isBlank()
                || properties.getKeySecret() == null || properties.getKeySecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Razorpay is not configured");
        }
    }
}
