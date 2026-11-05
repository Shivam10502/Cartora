package com.ecom.cartora.payment;

import com.ecom.cartora.order.entity.Order;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.repository.OrderRepo;
import com.ecom.cartora.payment.entity.Payment;
import com.ecom.cartora.payment.entity.PaymentRefund;
import com.ecom.cartora.payment.entity.PaymentStatus;
import com.ecom.cartora.payment.entity.RefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundServiceTest {
    private final RazorpayProperties properties = new RazorpayProperties();
    private final RestClient restClient = mock(RestClient.class);
    private final PaymentRepo paymentRepo = mock(PaymentRepo.class);
    private final PaymentRefundRepo refundRepo = mock(PaymentRefundRepo.class);
    private final OrderRepo orderRepo = mock(OrderRepo.class);
    private final RefundService service = new RefundService(properties, restClient, paymentRepo, refundRepo, orderRepo);

    private Order order;
    private Payment payment;
    private PaymentRefund refund;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(11L);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTotalAmount(new BigDecimal("100.00"));
        payment = new Payment();
        payment.setId(22L);
        payment.setOrder(order);
        payment.setAmount(new BigDecimal("100.00"));
        payment.setStatus(PaymentStatus.REFUND_PENDING);
        refund = new PaymentRefund();
        refund.setId(33L);
        refund.setPayment(payment);
        refund.setProviderRefundId("rf_1");
        refund.setReceipt("receipt_1");
        refund.setIdempotencyKey("idempotency_1");
        refund.setAmount(new BigDecimal("40.00"));
        refund.setStatus(RefundStatus.PENDING);
        when(refundRepo.findByProviderRefundId("rf_1")).thenReturn(Optional.of(refund));
        when(orderRepo.findByIdForUpdate(11L)).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderIdForUpdate(11L)).thenReturn(Optional.of(payment));
        when(refundRepo.findByPaymentIdOrderByCreatedAtDesc(22L)).thenReturn(List.of(refund));
        when(refundRepo.findByPaymentIdOrderByCreatedAtDescForUpdate(22L)).thenReturn(List.of(refund));
        when(refundRepo.findByProviderRefundId("rf_1")).thenReturn(Optional.of(refund));
    }

    @Test
    void processedPartialRefundKeepsAccuratePartialStateAndDoesNotCloseOrder() {
        service.updateRefundStatus("rf_1", true);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(paymentRepo).save(payment);
        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    void failedRefundReturnsPaymentToCapturedState() {
        service.updateRefundStatus("rf_1", false);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void refundWebhookCanResolveAnAmbiguousRequestIntent() {
        refund.setStatus(RefundStatus.REQUESTED);
        service.updateRefundStatus("rf_1", true);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void duplicateRefundWebhookDoesNotReapplyTerminalTransition() {
        refund.setStatus(RefundStatus.PROCESSED);

        service.updateRefundStatus("rf_1", false);

        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSED);
        verify(orderRepo).findByIdForUpdate(11L);
        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    void ambiguousRefundCanBeRetriedWithTheSameDurableIdempotencyKey() {
        refund.setStatus(RefundStatus.REQUESTED);
        when(refundRepo.findByPaymentIdOrderByCreatedAtDesc(22L)).thenReturn(List.of(refund));
        when(refundRepo.findByPaymentIdOrderByCreatedAtDescForUpdate(22L)).thenReturn(List.of(refund));
        org.springframework.http.client.ClientHttpRequestFactory requestFactory = (uri, method) -> {
            throw new java.io.IOException("timeout");
        };
        RestClient timeoutClient = RestClient.builder().requestFactory(requestFactory).baseUrl("https://example.test")
                .build();
        RefundService timeoutService = new RefundService(properties, timeoutClient, paymentRepo, refundRepo, orderRepo);
        properties.setKeyId("key");
        properties.setKeySecret("secret");

        var response = timeoutService.refundLateCapture(payment, new BigDecimal("40.00"));

        assertThat(response.status()).isEqualTo(RefundStatus.REQUESTED);
        verify(refundRepo, never()).saveAndFlush(any(PaymentRefund.class));
        assertThat(refund.getIdempotencyKey()).isEqualTo("idempotency_1");
    }
}
