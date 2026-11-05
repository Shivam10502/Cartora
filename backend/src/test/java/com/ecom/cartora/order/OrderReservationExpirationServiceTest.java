package com.ecom.cartora.order;

import com.ecom.cartora.order.entity.Order;
import com.ecom.cartora.order.entity.OrderItem;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.repository.OrderItemRepo;
import com.ecom.cartora.order.repository.OrderRepo;
import com.ecom.cartora.payment.PaymentRepo;
import com.ecom.cartora.payment.entity.Payment;
import com.ecom.cartora.payment.entity.PaymentStatus;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductInventoryService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderReservationExpirationServiceTest {
    private final OrderRepo orderRepo = mock(OrderRepo.class);
    private final OrderItemRepo itemRepo = mock(OrderItemRepo.class);
    private final PaymentRepo paymentRepo = mock(PaymentRepo.class);
    private final ProductInventoryService inventoryService = mock(ProductInventoryService.class);
    private final OrderReservationExpirationService service = new OrderReservationExpirationService(
            orderRepo, itemRepo, paymentRepo, inventoryService);

    @Test
    void expiryReleasesReservedStockAndMarksPendingPaymentFailed() {
        Order order = new Order();
        order.setId(4L);
        order.setStatus(OrderStatus.PENDING);
        order.setInventoryReserved(true);
        when(orderRepo.findByIdForUpdate(4L)).thenReturn(Optional.of(order));
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.PENDING);
        when(paymentRepo.findByOrderIdForUpdate(4L)).thenReturn(Optional.of(payment));
        Product product = new Product();
        product.setId(8L);
        product.setStockQuantity(2);
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(3);
        when(itemRepo.findByOrderId(4L)).thenReturn(List.of(item));
        when(inventoryService.lockAndRefresh(List.of(8L))).thenReturn(Map.of(8L, product));

        boolean expired = service.expireReservation(4L);

        assertThat(expired).isTrue();
        assertThat(product.getStockQuantity()).isEqualTo(5);
        assertThat(order.isInventoryReserved()).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureReason()).contains("expired");
        verify(orderRepo).save(order);
        verify(paymentRepo).save(payment);
    }

    @Test
    void expiryDoesNotReleaseStockWhenPaymentAlreadySucceeded() {
        Order order = new Order();
        order.setId(4L);
        order.setStatus(OrderStatus.PENDING);
        order.setInventoryReserved(true);
        when(orderRepo.findByIdForUpdate(4L)).thenReturn(Optional.of(order));
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepo.findByOrderIdForUpdate(4L)).thenReturn(Optional.of(payment));

        assertThat(service.expireReservation(4L)).isFalse();
        verify(itemRepo, never()).findByOrderId(4L);
        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    void scannerFindsOnlyOrdersOlderThanTheConfiguredCutoff() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        when(orderRepo.findExpiredReservationOrderIds(eq(cutoff), any())).thenReturn(List.of(3L, 4L));

        assertThat(service.findExpiredReservationIds(cutoff)).containsExactly(3L, 4L);
        verify(orderRepo).findExpiredReservationOrderIds(eq(cutoff), any());
    }
}
