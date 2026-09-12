package com.ecom.cartora.order;

import com.ecom.cartora.cart.CartItemRepo;
import com.ecom.cartora.cart.CartRepo;
import com.ecom.cartora.cart.entity.Cart;
import com.ecom.cartora.cart.entity.CartItem;
import com.ecom.cartora.exception.CartIsEmptyException;
import com.ecom.cartora.exception.CartNotFoundException;
import com.ecom.cartora.exception.InsufficientStockException;
import com.ecom.cartora.exception.OrderCancellationException;
import com.ecom.cartora.exception.OrderNotFoundException;
import com.ecom.cartora.order.dto.OrderItemResponse;
import com.ecom.cartora.order.dto.OrderResponse;
import com.ecom.cartora.order.entity.Order;
import com.ecom.cartora.order.entity.OrderItem;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.repository.OrderItemRepo;
import com.ecom.cartora.order.repository.OrderRepo;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    @Autowired
    private OrderRepo orderRepo;

    @Autowired
    private OrderItemRepo orderItemRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private CartRepo cartRepo;

    @Autowired
    private CartItemRepo cartItemRepo;


    private User getUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();

        return userRepo.findByEmail(username)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));
    }


    private OrderItemResponse buildOrderItemResponse(OrderItem item) {

        OrderItemResponse response = new OrderItemResponse();

        response.setProductId(item.getProduct().getId());
        response.setProductName(item.getProduct().getName());
        response.setQuantity(item.getQuantity());
        response.setPrice(item.getPrice());
        response.setSubtotal(item.getSubtotal());

        return response;
    }


    private OrderResponse buildOrderResponse(Order order) {

        List<OrderItem> items =
                orderItemRepo.findByOrderId(order.getId());

        List<OrderItemResponse> itemResponses =
                new ArrayList<>();

        for (OrderItem item : items) {
            itemResponses.add(
                    buildOrderItemResponse(item)
            );
        }

        OrderResponse response = new OrderResponse();

        response.setOrderId(order.getId());
        response.setTotalAmount(order.getTotalAmount());
        response.setStatus(order.getStatus());
        response.setOrderItems(itemResponses);
        response.setCreatedAt(order.getCreatedAt());

        return response;
    }


    @Transactional
    public OrderResponse createOrder() {

        User user = getUser();

        Cart cart = cartRepo.findByUserId(user.getId())
                .orElseThrow(() ->
                        new CartNotFoundException("Cart Not Found"));

        List<CartItem> cartItems =
                cartItemRepo.findByCartId(cart.getId());

        if (cartItems.isEmpty()) {
            throw new CartIsEmptyException("Cart Is Empty");
        }

        for (CartItem item : cartItems) {

            Product product = item.getProduct();

            if (item.getQuantity() > product.getStockQuantity()) {
                throw new InsufficientStockException(
                        "Required quantity is not available for product: "
                                + product.getName()
                );
            }
        }

        Order order = new Order();

        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(BigDecimal.ZERO);

        LocalDateTime now = LocalDateTime.now();

        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        orderRepo.save(order);

        BigDecimal totalPrice = BigDecimal.ZERO;

        for (CartItem item : cartItems) {

            Product product = item.getProduct();

            OrderItem orderItem = new OrderItem();

            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(item.getQuantity());

            BigDecimal price = product.getPrice();

            orderItem.setPrice(price);

            BigDecimal subtotal =
                    price.multiply(
                            BigDecimal.valueOf(item.getQuantity())
                    );

            orderItem.setSubtotal(subtotal);

            totalPrice = totalPrice.add(subtotal);

            product.setStockQuantity(
                    product.getStockQuantity()
                            - item.getQuantity()
            );

            orderItemRepo.save(orderItem);
        }

        order.setTotalAmount(totalPrice);
        order.setUpdatedAt(LocalDateTime.now());

        orderRepo.save(order);

        cartItemRepo.deleteByCartId(cart.getId());

        return buildOrderResponse(order);
    }


    public List<OrderResponse> getMyOrders() {

        User user = getUser();

        List<Order> orders =
                orderRepo.findByUserId(user.getId());

        List<OrderResponse> responses =
                new ArrayList<>();

        for (Order order : orders) {
            responses.add(
                    buildOrderResponse(order)
            );
        }

        return responses;
    }


    public OrderResponse getOrderById(Long orderId) {

        User user = getUser();

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() ->
                        new OrderNotFoundException(
                                "Order Doesn't Exist"));

        if (!user.getId().equals(order.getUser().getId())) {
            throw new RuntimeException(
                    "Order does not belong to the authenticated user"
            );
        }

        return buildOrderResponse(order);
    }


    @Transactional
    public OrderResponse cancelOrder(Long orderId) {

        User user = getUser();

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() ->
                        new OrderNotFoundException(
                                "Order Doesn't Exist"));

        if (!user.getId().equals(order.getUser().getId())) {
            throw new RuntimeException(
                    "Order does not belong to the authenticated user"
            );
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderCancellationException(
                    order.getStatus()
                            + " Order Cannot Be Cancelled"
            );
        }

        List<OrderItem> items =
                orderItemRepo.findByOrderId(orderId);

        for (OrderItem item : items) {

            Product product = item.getProduct();

            product.setStockQuantity(
                    product.getStockQuantity()
                            + item.getQuantity()
            );
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());

        orderRepo.save(order);

        return buildOrderResponse(order);
    }
}