package com.ecom.cartora.payment;

import com.ecom.cartora.order.entity.Order;
import com.ecom.cartora.order.entity.OrderItem;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.repository.OrderItemRepo;
import com.ecom.cartora.order.repository.OrderRepo;
import com.ecom.cartora.payment.dto.PaymentRequest;
import com.ecom.cartora.payment.dto.PaymentResponse;
import com.ecom.cartora.payment.entity.Payment;
import com.ecom.cartora.payment.entity.PaymentMethod;
import com.ecom.cartora.payment.entity.PaymentStatus;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductInventoryService;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepo paymentRepo;
    @Mock
    private UserRepo userRepo;
    @Mock
    private OrderRepo orderRepo;
    @Mock
    private OrderItemRepo orderItemRepo;
    @Mock
    private com.ecom.cartora.cart.repository.CartRepo cartRepo;
    @Mock
    private com.ecom.cartora.cart.repository.CartItemRepo cartItemRepo;
    @Mock
    private SimulatedPaymentProcessor simulatedPaymentProcessor;
    @Mock
    private ProductInventoryService productInventoryService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    private User user;
    private Order order;
    private Product product;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "customer@cartora.test",
                        "password"));

        user = new User();
        user.setId(7L);
        user.setEmail("customer@cartora.test");

        order = new Order();
        order.setId(21L);
        order.setUser(user);
        order.setTotalAmount(new BigDecimal("50.00"));
        order.setStatus(OrderStatus.PENDING);

        product = new Product();
        product.setId(9L);
        product.setName("Desk Lamp");
        product.setStockQuantity(3);

        orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.setQuantity(2);

        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(orderRepo.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderId(order.getId())).thenReturn(Optional.empty());
        when(paymentRepo.save(any(Payment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void makePayment_shouldLeaveOrderPending_whenSimulationFails() {
        // Arrange
        when(simulatedPaymentProcessor.process(PaymentMethod.UPI)).thenReturn(false);
        PaymentRequest request = new PaymentRequest(order.getId(), PaymentMethod.UPI);

        // Act
        PaymentResponse response = paymentService.makePayment(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(product.getStockQuantity()).isEqualTo(3);
        verify(orderRepo).save(order);
        verify(cartItemRepo, never()).deleteByCartId(any());
    }

    @Test
    void makePayment_shouldConfirmOrderAndDeductStock_whenSimulationSucceeds() {
        // Arrange
        when(simulatedPaymentProcessor.process(PaymentMethod.CARD)).thenReturn(true);
        when(orderItemRepo.findByOrderId(order.getId())).thenReturn(List.of(orderItem));
        when(productInventoryService.lockAndRefresh(any())).thenReturn(java.util.Map.of(product.getId(), product));
        when(com.ecom.cartora.cart.repository.CartRepo.class.cast(cartRepo)
                .findByUserId(user.getId())).thenReturn(Optional.empty());
        PaymentRequest request = new PaymentRequest(order.getId(), PaymentMethod.CARD);

        // Act
        PaymentResponse response = paymentService.makePayment(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(product.getStockQuantity()).isEqualTo(1);
        verify(orderRepo).save(order);
    }

    @Test
    void makePayment_shouldPersistFailure_whenStockRunsOutAfterApproval() {
        // Arrange
        when(simulatedPaymentProcessor.process(PaymentMethod.UPI)).thenReturn(true);
        product.setStockQuantity(1);
        when(orderItemRepo.findByOrderId(order.getId())).thenReturn(List.of(orderItem));
        when(productInventoryService.lockAndRefresh(any())).thenReturn(java.util.Map.of(product.getId(), product));
        PaymentRequest request = new PaymentRequest(order.getId(), PaymentMethod.UPI);

        // Act
        PaymentResponse response = paymentService.makePayment(request);

        // Assert
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(product.getStockQuantity()).isEqualTo(1);
        verify(paymentRepo, org.mockito.Mockito.times(2)).save(any(Payment.class));
        verify(orderRepo, never()).save(order);
    }
}
