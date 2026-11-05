package com.ecom.cartora.payment;

import com.ecom.cartora.cart.repository.CartItemRepo;
import com.ecom.cartora.cart.repository.CartRepo;
import com.ecom.cartora.order.entity.Order;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.repository.OrderItemRepo;
import com.ecom.cartora.order.repository.OrderRepo;
import com.ecom.cartora.payment.dto.RazorpayOrderResponse;
import com.ecom.cartora.payment.entity.Payment;
import com.ecom.cartora.payment.entity.PaymentMethod;
import com.ecom.cartora.payment.entity.PaymentStatus;
import com.ecom.cartora.payment.entity.PaymentWebhookEvent;
import com.ecom.cartora.product.ProductInventoryService;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class RazorpayPaymentServiceTest {

    private final RazorpayProperties properties = new RazorpayProperties();
    private final RestClient restClient = mock(RestClient.class);
    private final UserRepo userRepo = mock(UserRepo.class);
    private final OrderRepo orderRepo = mock(OrderRepo.class);
    private final PaymentRepo paymentRepo = mock(PaymentRepo.class);
    private final OrderItemRepo orderItemRepo = mock(OrderItemRepo.class);
    private final PaymentWebhookEventRepo webhookEventRepo = mock(PaymentWebhookEventRepo.class);
    private final ProductInventoryService inventoryService = mock(ProductInventoryService.class);
    private final CartRepo cartRepo = mock(CartRepo.class);
    private final CartItemRepo cartItemRepo = mock(CartItemRepo.class);
    private final RefundService refundService = mock(RefundService.class);

    private final RazorpayPaymentService service = new RazorpayPaymentService(
            properties,
            restClient,
            new ObjectMapper(),
            userRepo,
            orderRepo,
            orderItemRepo,
            paymentRepo,
            webhookEventRepo,
            inventoryService,
            cartRepo,
            cartItemRepo,
            refundService,
            event -> { });

    private User user;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("customer@cartora.test", "password"));
        properties.setKeyId("rzp_test_key");
        properties.setKeySecret("test-secret");

        user = new User();
        user.setId(7L);
        user.setEmail("customer@cartora.test");

        order = new Order();
        order.setId(21L);
        order.setUser(user);
        order.setTotalAmount(new BigDecimal("12.50"));
        order.setStatus(OrderStatus.PENDING);
        order.setInventoryReserved(true);

        payment = new Payment();
        payment.setId(31L);
        payment.setOrder(order);
        payment.setAmount(order.getTotalAmount());
        payment.setPaymentMethod(PaymentMethod.RAZORPAY);
        payment.setStatus(PaymentStatus.FAILED);
        payment.setAttemptNumber(1);
        payment.setProviderOrderId("order_razorpay_original");
        payment.setFailureReason("Issuer declined the payment");

        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(orderRepo.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderIdForUpdate(order.getId())).thenReturn(Optional.of(payment));
        when(paymentRepo.save(payment)).thenReturn(payment);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void retriesFailedPaymentOnSameGatewayOrderSoDelayedCallbacksRemainReconcilable() {
        RazorpayOrderResponse response = service.createGatewayOrder(order.getId());

        assertThat(response.razorpayOrderId()).isEqualTo("order_razorpay_original");
        assertThat(response.paymentId()).isEqualTo(payment.getId());
        assertThat(response.amount()).isEqualTo(1250L);
        assertThat(response.keyId()).isEqualTo("rzp_test_key");
        assertThat(payment.getProviderOrderId()).isEqualTo("order_razorpay_original");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAttemptNumber()).isEqualTo(2);
        assertThat(payment.getFailureReason()).isNull();
        verify(paymentRepo).save(payment);
        verifyNoInteractions(restClient);
    }

    @Test
    void webhookClaimsEventIdAtomicallyAndSkipsDuplicateDelivery() throws Exception {
        String body = "{\"event\":\"payment.failed\",\"payload\":{}}";
        properties.setWebhookSecret("webhook-secret");
        when(webhookEventRepo.insertIfAbsent(org.mockito.ArgumentMatchers.eq("evt_1"),
                org.mockito.ArgumentMatchers.eq("payment.failed"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);

        service.handleWebhook("evt_1", signature("webhook-secret", body), body.getBytes(StandardCharsets.UTF_8));

        verify(webhookEventRepo).insertIfAbsent(org.mockito.ArgumentMatchers.eq("evt_1"),
                org.mockito.ArgumentMatchers.eq("payment.failed"), org.mockito.ArgumentMatchers.any());
        verify(paymentRepo, never()).findByProviderOrderId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void webhookPersistsNewEventBeforeApplyingSupportedPaymentState() throws Exception {
        String body = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{"
                + "\"order_id\":\"order_razorpay_original\",\"id\":\"pay_1\",\"amount\":1250,"
                + "\"currency\":\"INR\",\"error_description\":\"declined\"}}}}";
        properties.setWebhookSecret("webhook-secret");
        when(webhookEventRepo.insertIfAbsent(org.mockito.ArgumentMatchers.eq("evt_2"),
                org.mockito.ArgumentMatchers.eq("payment.failed"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        when(paymentRepo.findByProviderOrderId("order_razorpay_original")).thenReturn(Optional.of(payment));
        when(orderRepo.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.handleWebhook("evt_2", signature("webhook-secret", body), body.getBytes(StandardCharsets.UTF_8));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).isEqualTo("declined");
        verify(paymentRepo).save(payment);
    }

    private static String signature(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
