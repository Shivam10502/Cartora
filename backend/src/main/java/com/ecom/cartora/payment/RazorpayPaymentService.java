package com.ecom.cartora.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecom.cartora.cart.entity.Cart;
import com.ecom.cartora.cart.entity.CartItem;
import com.ecom.cartora.cart.repository.CartItemRepo;
import com.ecom.cartora.cart.repository.CartRepo;
import com.ecom.cartora.exception.InsufficientStockException;
import com.ecom.cartora.exception.OrderNotFoundException;
import com.ecom.cartora.exception.PaymentCannotBeDone;
import com.ecom.cartora.exception.UserOrderNotFoundException;
import com.ecom.cartora.order.entity.Order;
import com.ecom.cartora.order.OrderPaidEvent;
import com.ecom.cartora.order.entity.OrderItem;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.repository.OrderItemRepo;
import com.ecom.cartora.order.repository.OrderRepo;
import com.ecom.cartora.payment.dto.PaymentResponse;
import com.ecom.cartora.payment.dto.RazorpayOrderResponse;
import com.ecom.cartora.payment.dto.RazorpayVerificationRequest;
import com.ecom.cartora.payment.entity.Payment;
import com.ecom.cartora.payment.entity.PaymentMethod;
import com.ecom.cartora.payment.entity.PaymentStatus;
import com.ecom.cartora.payment.entity.PaymentWebhookEvent;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductRepo;
import com.ecom.cartora.product.ProductInventoryService;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RazorpayPaymentService {

    private static final String CURRENCY = "INR";

    private final RazorpayProperties properties;
    private final RestClient razorpayRestClient;
    private final ObjectMapper objectMapper;
    private final UserRepo userRepo;
    private final OrderRepo orderRepo;
    private final OrderItemRepo orderItemRepo;
    private final PaymentRepo paymentRepo;
    private final PaymentWebhookEventRepo webhookEventRepo;
    private final ProductInventoryService productInventoryService;
    private final CartRepo cartRepo;
    private final CartItemRepo cartItemRepo;
    private final RefundService refundService;
    private final ApplicationEventPublisher eventPublisher;

    public RazorpayPaymentService(
            RazorpayProperties properties,
            RestClient razorpayRestClient,
            ObjectMapper objectMapper,
            UserRepo userRepo,
            OrderRepo orderRepo,
            OrderItemRepo orderItemRepo,
            PaymentRepo paymentRepo,
            PaymentWebhookEventRepo webhookEventRepo,
            ProductInventoryService productInventoryService,
            CartRepo cartRepo,
            CartItemRepo cartItemRepo,
            RefundService refundService,
            ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.razorpayRestClient = razorpayRestClient;
        this.objectMapper = objectMapper;
        this.userRepo = userRepo;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.paymentRepo = paymentRepo;
        this.webhookEventRepo = webhookEventRepo;
        this.productInventoryService = productInventoryService;
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.refundService = refundService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RazorpayOrderResponse createGatewayOrder(Long orderId) {
        requireCredentials();
        User user = getUser();
        Order order = orderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order doesn't exist"));
        verifyOwner(user, order);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new PaymentCannotBeDone("Only pending orders can be paid");
        }

        Payment payment = paymentRepo.findByOrderIdForUpdate(orderId).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new PaymentCannotBeDone("Payment has already been completed for this order");
        }
        if (payment != null && payment.getStatus() != PaymentStatus.PENDING
                && payment.getStatus() != PaymentStatus.FAILED) {
            throw new PaymentCannotBeDone("This payment cannot be retried in its current state");
        }
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING
                && payment.getProviderOrderId() != null) {
            return toGatewayResponse(order, payment);
        }

        // A Razorpay order can accept another payment attempt after a failed one.
        // Reusing it keeps every subsequent callback tied to a provider order we
        // still recognize, instead of overwriting the only stored provider ID.
        if (payment != null && payment.getStatus() == PaymentStatus.FAILED
                && payment.getProviderOrderId() != null) {
            payment.setAttemptNumber(payment.getAttemptNumber() + 1);
            payment.setStatus(PaymentStatus.PENDING);
            payment.setFailureReason(null);
            payment.setTransactionId(UUID.randomUUID().toString());
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepo.save(payment);
            return toGatewayResponse(order, payment);
        }

        reserveInventoryIfNeeded(order);
        long amount = toMinorUnits(order.getTotalAmount());
        Map<String, Object> request = Map.of(
                "amount", amount,
                "currency", CURRENCY,
                "receipt", "cartora_" + order.getId(),
                "notes", Map.of("cartoraOrderId", order.getId().toString()));

        JsonNode response = razorpayRequest(() -> razorpayRestClient.post()
                .uri("/orders")
                .headers(headers -> headers.setBasicAuth(properties.getKeyId(), properties.getKeySecret()))
                .body(request)
                .retrieve()
                .body(JsonNode.class));
        String gatewayOrderId = response == null ? null : response.path("id").asText(null);
        if (gatewayOrderId == null || gatewayOrderId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider returned an invalid order");
        }

        LocalDateTime now = LocalDateTime.now();
        if (payment == null) {
            payment = new Payment();
            payment.setOrder(order);
            payment.setCreatedAt(now);
            payment.setAttemptNumber(1);
        } else {
            payment.setAttemptNumber(payment.getAttemptNumber() + 1);
        }
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod(PaymentMethod.RAZORPAY);
        payment.setProviderOrderId(gatewayOrderId);
        payment.setProviderPaymentId(null);
        payment.setFailureReason(null);
        payment.setTransactionId(UUID.randomUUID().toString());
        payment.setUpdatedAt(now);
        paymentRepo.save(payment);
        return toGatewayResponse(order, payment);
    }

    @Transactional
    public PaymentResponse verifyPayment(RazorpayVerificationRequest request) {
        requireCredentials();
        User user = getUser();
        Order order = orderRepo.findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new OrderNotFoundException("Order doesn't exist"));
        verifyOwner(user, order);
        Payment payment = paymentRepo.findByOrderIdForUpdate(order.getId())
                .orElseThrow(() -> new PaymentCannotBeDone("No payment attempt exists for this order"));
        if (payment.getStatus() == PaymentStatus.SUCCESS) return toPaymentResponse(payment);
        if (payment.getStatus() == PaymentStatus.REFUND_PENDING
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new PaymentCannotBeDone("A refund is pending or has already been issued for this payment");
        }
        if ((order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CANCELLED)
                || !request.razorpayOrderId().equals(payment.getProviderOrderId())) {
            throw new PaymentCannotBeDone("The payment does not match this pending order");
        }
        String signedPayload = payment.getProviderOrderId() + "|" + request.razorpayPaymentId();
        if (!RazorpaySignatureVerifier.matches(properties.getKeySecret(), signedPayload,
                request.razorpaySignature())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment signature is invalid");
        }

        JsonNode providerPayment = razorpayRequest(() -> razorpayRestClient.get()
                .uri("/payments/{paymentId}", request.razorpayPaymentId())
                .headers(headers -> headers.setBasicAuth(properties.getKeyId(), properties.getKeySecret()))
                .retrieve()
                .body(JsonNode.class));
        if (providerPayment == null
                || !payment.getProviderOrderId().equals(providerPayment.path("order_id").asText())
                || providerPayment.path("amount").asLong(-1) != toMinorUnits(payment.getAmount())
                || !CURRENCY.equals(providerPayment.path("currency").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment details do not match the order");
        }
        String providerStatus = providerPayment.path("status").asText();
        if (!"captured".equalsIgnoreCase(providerStatus)) {
            if ("failed".equalsIgnoreCase(providerStatus)) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(providerPayment.path("error_description").asText("Payment failed"));
                payment.setUpdatedAt(LocalDateTime.now());
                paymentRepo.save(payment);
                return toPaymentResponse(payment);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment has not been captured");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            payment.setProviderPaymentId(request.razorpayPaymentId());
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepo.save(payment);
            refundService.refundLateCapture(payment, payment.getAmount());
            return toPaymentResponse(payment);
        }
        completeCapturedPayment(order, payment, request.razorpayPaymentId());
        return toPaymentResponse(payment);
    }

    @Transactional
    public void handleWebhook(String eventId, String signature, byte[] rawBody) {
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment webhook is not configured");
        }
        String payload = new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
        if (!RazorpaySignatureVerifier.matches(properties.getWebhookSecret(), payload, signature)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook signature is invalid");
        }
        if (eventId == null || eventId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook event ID is required");
        }
        JsonNode event;
        try {
            event = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook payload is invalid");
        }
        String eventType = event.path("event").asText();
        int inserted = webhookEventRepo.insertIfAbsent(eventId,
                eventType.isBlank() ? "unknown" : eventType, LocalDateTime.now());
        if (inserted == 0) return;

        if ("refund.processed".equals(eventType) || "refund.failed".equals(eventType)) {
            String refundId = event.path("payload").path("refund").path("entity").path("id").asText();
            if (refundId.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund event is missing its ID");
            refundService.updateRefundStatus(refundId, "refund.processed".equals(eventType));
            return;
        }
        if (!"payment.captured".equals(eventType) && !"payment.failed".equals(eventType)) return;
        JsonNode providerPayment = event.path("payload").path("payment").path("entity");
        String providerOrderId = providerPayment.path("order_id").asText();
        String providerPaymentId = providerPayment.path("id").asText();
        Payment payment = paymentRepo.findByProviderOrderId(providerOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment attempt was not found"));
        Order order = orderRepo.findByIdForUpdate(payment.getOrder().getId())
                .orElseThrow(() -> new OrderNotFoundException("Order doesn't exist"));
        if (payment.getStatus() == PaymentStatus.SUCCESS) return;
        if (providerPayment.path("amount").asLong(-1) != toMinorUnits(payment.getAmount())
                || !CURRENCY.equals(providerPayment.path("currency").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook payment details do not match the order");
        }
        if ("payment.captured".equals(eventType)) {
            if (order.getStatus() == OrderStatus.CANCELLED) {
                payment.setProviderPaymentId(providerPaymentId);
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setUpdatedAt(LocalDateTime.now());
                paymentRepo.save(payment);
                refundService.refundLateCapture(payment, payment.getAmount());
                return;
            }
            if (order.getStatus() != OrderStatus.PENDING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Captured payment belongs to a non-pending order");
            }
            completeCapturedPayment(order, payment, providerPaymentId);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(providerPayment.path("error_description").asText("Payment failed"));
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepo.save(payment);
        }
    }

    private void reserveInventoryIfNeeded(Order order) {
        if (order.isInventoryReserved()) return;
        List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());
        Map<Long, Product> products = lockProducts(items);
        for (OrderItem item : items) {
            Product product = products.get(item.getProduct().getId());
            if (item.getQuantity() > product.getStockQuantity()) {
                throw new InsufficientStockException("Insufficient stock for product: " + product.getName());
            }
        }
        for (OrderItem item : items) {
            Product product = products.get(item.getProduct().getId());
            product.setStockQuantity(product.getStockQuantity() - item.getQuantity());
        }
        order.setInventoryReserved(true);
    }

    private Map<Long, Product> lockProducts(List<OrderItem> items) {
        return productInventoryService.lockAndRefresh(items.stream()
                .map(item -> item.getProduct().getId()).toList());
    }

    private void completeCapturedPayment(Order order, Payment payment, String providerPaymentId) {
        if (!order.isInventoryReserved()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inventory reservation is no longer active");
        }
        LocalDateTime now = LocalDateTime.now();
        payment.setProviderPaymentId(providerPaymentId);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setFailureReason(null);
        payment.setUpdatedAt(now);
        paymentRepo.save(payment);
        order.setInventoryReserved(false);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setUpdatedAt(now);
        orderRepo.save(order);
        eventPublisher.publishEvent(new OrderPaidEvent(order.getUser().getEmail(), order.getUser().getName(),
                order.getId(), order.getTotalAmount()));
        Cart cart = cartRepo.findByUserId(order.getUser().getId()).orElse(null);
        if (cart != null) {
            cartItemRepo.deleteByCartId(cart.getId());
            cart.setUpdatedAt(now);
            cartRepo.save(cart);
        }
    }

    private RazorpayOrderResponse toGatewayResponse(Order order, Payment payment) {
        return new RazorpayOrderResponse(order.getId(), payment.getId(), payment.getProviderOrderId(),
                properties.getKeyId(), toMinorUnits(payment.getAmount()), CURRENCY);
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrder().getId(), payment.getAmount(),
                payment.getStatus(), payment.getPaymentMethod(), payment.getTransactionId(),
                payment.getCreatedAt(), payment.getProviderOrderId(), payment.getProviderPaymentId(),
                payment.getFailureReason());
    }

    private User getUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return userRepo.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private void verifyOwner(User user, Order order) {
        if (!user.getId().equals(order.getUser().getId())) {
            throw new UserOrderNotFoundException("Order does not belong to the authenticated user");
        }
    }

    private void requireCredentials() {
        if (properties.getKeyId() == null || properties.getKeyId().isBlank()
                || properties.getKeySecret() == null || properties.getKeySecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Razorpay is not configured. Add test credentials on the backend.");
        }
    }

    private long toMinorUnits(BigDecimal amount) {
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw new PaymentCannotBeDone("Payment amount must have at most two decimal places");
        }
    }

    private <T> T razorpayRequest(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment provider request failed", ex);
        }
    }
}
