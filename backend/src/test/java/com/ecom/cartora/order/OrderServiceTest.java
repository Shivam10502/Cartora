package com.ecom.cartora.order;

import com.ecom.cartora.cart.repository.CartItemRepo;
import com.ecom.cartora.cart.repository.CartRepo;
import com.ecom.cartora.checkout.dto.ShippingAddressRequest;
import com.ecom.cartora.cart.entity.Cart;
import com.ecom.cartora.cart.entity.CartItem;
import com.ecom.cartora.coupon.CouponDiscount;
import com.ecom.cartora.coupon.CouponService;
import com.ecom.cartora.exception.UserOrderNotFoundException;
import com.ecom.cartora.exception.OrderCancellationException;
import com.ecom.cartora.order.entity.Order;
import com.ecom.cartora.order.entity.OrderItem;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.dto.OrderResponse;
import com.ecom.cartora.order.repository.OrderItemRepo;
import com.ecom.cartora.order.repository.OrderRepo;
import com.ecom.cartora.payment.PaymentRepo;
import com.ecom.cartora.payment.entity.Payment;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepo orderRepo;
    @Mock
    private OrderItemRepo orderItemRepo;
    @Mock
    private UserRepo userRepo;
    @Mock
    private CartRepo cartRepo;
    @Mock
    private CartItemRepo cartItemRepo;
    @Mock
    private ProductInventoryService productInventoryService;
    @Mock
    private CouponService couponService;
    @Mock
    private PaymentRepo paymentRepo;

    @InjectMocks
    private OrderService orderService;

    private User customer;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setId(1L);
        customer.setEmail("customer@cartora.test");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(customer.getEmail(), "password"));
        lenient().when(userRepo.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getOrderById_shouldRejectOrderOwnedByAnotherCustomer() {
        // Arrange
        User anotherCustomer = new User();
        anotherCustomer.setId(2L);
        Order order = new Order();
        order.setId(5L);
        order.setUser(anotherCustomer);
        when(orderRepo.findById(5L)).thenReturn(Optional.of(order));

        // Act + Assert
        assertThatThrownBy(() -> orderService.getOrderById(5L))
                .isInstanceOf(UserOrderNotFoundException.class);
        verify(orderItemRepo, never()).findByOrderId(5L);
    }

    @Test
    void cancelOrder_shouldLeaveStockUnchangedAndMarkPendingOrderCancelled() {
        // Arrange
        Order order = new Order();
        order.setId(8L);
        order.setUser(customer);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("40.00"));

        Product product = new Product();
        product.setId(3L);
        product.setName("Desk Lamp");
        product.setStockQuantity(2);

        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("20.00"));
        item.setSubtotal(new BigDecimal("40.00"));

        when(orderRepo.findByIdForUpdate(8L)).thenReturn(Optional.of(order));
        order.setInventoryReserved(true);
        when(productInventoryService.lockAndRefresh(any())).thenReturn(java.util.Map.of(product.getId(), product));
        when(orderItemRepo.findByOrderId(8L)).thenReturn(List.of(item));

        // Act
        orderService.cancelOrder(8L);

        // Assert
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStockQuantity()).isEqualTo(4);
        assertThat(order.isInventoryReserved()).isFalse();
        verify(orderRepo).save(order);
    }

    @Test
    void cancelOrder_shouldRejectWhileGatewayPaymentIsCaptured() {
        Order order = new Order();
        order.setId(9L);
        order.setUser(customer);
        order.setStatus(OrderStatus.PENDING);
        when(orderRepo.findByIdForUpdate(9L)).thenReturn(Optional.of(order));
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.SUCCESS);
        when(paymentRepo.findByOrderIdForUpdate(9L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> orderService.cancelOrder(9L))
                .isInstanceOf(OrderCancellationException.class);
        verify(orderRepo, never()).save(order);
    }

    @Test
    void updateOrderStatus_shouldAdvanceConfirmedOrderToShipped() {
        // Arrange
        Order order = new Order();
        order.setId(13L);
        order.setUser(customer);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTotalAmount(new BigDecimal("40.00"));
        when(orderRepo.findByIdForUpdate(13L)).thenReturn(Optional.of(order));
        when(orderItemRepo.findByOrderId(13L)).thenReturn(List.of());

        // Act
        var response = orderService.updateOrderStatus(13L, OrderStatus.SHIPPED);

        // Assert
        assertThat(response.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepo).save(order);
    }

    @Test
    void updateOrderStatus_shouldRejectSkippingShippingStage() {
        // Arrange
        Order order = new Order();
        order.setId(14L);
        order.setStatus(OrderStatus.CONFIRMED);
        when(orderRepo.findByIdForUpdate(14L)).thenReturn(Optional.of(order));

        // Act + Assert
        assertThatThrownBy(() -> orderService.updateOrderStatus(14L, OrderStatus.DELIVERED))
                .isInstanceOf(OrderCancellationException.class);
        verify(orderRepo, never()).save(order);
    }

    @Test
    void createOrder_shouldPersistDiscountedTotalFromValidatedCoupon() {
        // Arrange
        Cart cart = new Cart();
        cart.setId(6L);
        cart.setUser(customer);

        Product product = new Product();
        product.setId(22L);
        product.setName("Desk Lamp");
        product.setPrice(new BigDecimal("50.00"));
        product.setStockQuantity(5);

        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(2);

        OrderItem responseItem = new OrderItem();
        responseItem.setProduct(product);
        responseItem.setQuantity(2);
        responseItem.setPrice(new BigDecimal("50.00"));
        responseItem.setSubtotal(new BigDecimal("100.00"));

        when(cartRepo.findByUserIdForUpdate(customer.getId())).thenReturn(Optional.of(cart));
        when(cartItemRepo.findByCartId(cart.getId())).thenReturn(List.of(cartItem));
        when(productInventoryService.lockAndRefresh(any())).thenReturn(java.util.Map.of(product.getId(), product));
        when(couponService.calculateDiscount("SAVE10", new BigDecimal("100.00")))
                .thenReturn(new CouponDiscount("SAVE10", new BigDecimal("10.00")));
        when(orderRepo.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        when(orderItemRepo.findByOrderId(99L)).thenReturn(List.of(responseItem));

        // Act
        ShippingAddressRequest shippingAddress = new ShippingAddressRequest(
                "Asha Rao", "12 Park Street", "Near the library",
                "Pune", "Maharashtra", "411001", "India");
        OrderResponse response = orderService.createOrder("SAVE10", shippingAddress);

        // Assert
        assertThat(response.getTotalAmount()).isEqualByComparingTo("90.00");
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("10.00");
        assertThat(response.getCouponCode()).isEqualTo("SAVE10");
        assertThat(response.getShippingAddress()).isEqualTo(shippingAddress);
        assertThat(product.getStockQuantity()).isEqualTo(3);
        verify(cartItemRepo).deleteByCartId(cart.getId());
    }
}
