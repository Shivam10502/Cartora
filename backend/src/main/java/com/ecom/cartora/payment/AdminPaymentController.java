package com.ecom.cartora.payment;

import com.ecom.cartora.payment.dto.PaymentResponse;
import com.ecom.cartora.payment.dto.RefundRequest;
import com.ecom.cartora.payment.dto.RefundResponse;
import com.ecom.cartora.payment.entity.Payment;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final PaymentRepo paymentRepo;
    private final PaymentRefundRepo refundRepo;
    private final RefundService refundService;

    public AdminPaymentController(PaymentRepo paymentRepo, PaymentRefundRepo refundRepo, RefundService refundService) {
        this.paymentRepo = paymentRepo;
        this.refundRepo = refundRepo;
        this.refundService = refundService;
    }

    @GetMapping
    public List<PaymentResponse> listPayments() {
        return paymentRepo.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{paymentId}/refunds")
    public List<RefundResponse> listRefunds(@PathVariable("paymentId") Long paymentId) {
        return refundRepo.findByPaymentIdOrderByCreatedAtDesc(paymentId).stream()
                .map(refund -> new RefundResponse(refund.getId(), refund.getPayment().getId(),
                        refund.getPayment().getOrder().getId(), refund.getProviderRefundId(), refund.getReceipt(),
                        refund.getAmount(), refund.getStatus(), refund.getCreatedAt()))
                .toList();
    }

    @PostMapping("/{paymentId}/refunds")
    public RefundResponse refundPayment(@PathVariable("paymentId") Long paymentId,
            @Valid @RequestBody(required = false) RefundRequest request) {
        return refundService.refundPayment(paymentId, request);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrder().getId(), payment.getAmount(),
                payment.getStatus(), payment.getPaymentMethod(), payment.getTransactionId(),
                payment.getCreatedAt(), payment.getProviderOrderId(), payment.getProviderPaymentId(),
                payment.getFailureReason());
    }
}
