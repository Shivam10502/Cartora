package com.ecom.cartora.order;

import com.ecom.cartora.cart.repository.CartItemRepo;
import com.ecom.cartora.cart.repository.CartRepo;
import com.ecom.cartora.cart.entity.Cart;
import com.ecom.cartora.cart.entity.CartItem;
import com.ecom.cartora.checkout.dto.ShippingAddressRequest;
import com.ecom.cartora.coupon.CouponDiscount;
import com.ecom.cartora.coupon.CouponService;
import com.ecom.cartora.exception.CartIsEmptyException;
import com.ecom.cartora.exception.CartNotFoundException;
import com.ecom.cartora.exception.InsufficientStockException;
import com.ecom.cartora.exception.OrderCancellationException;
import com.ecom.cartora.exception.OrderNotFoundException;
import com.ecom.cartora.exception.UserOrderNotFoundException;
import com.ecom.cartora.order.dto.OrderItemResponse;
import com.ecom.cartora.order.dto.OrderResponse;
import com.ecom.cartora.order.entity.Order;
import com.ecom.cartora.order.entity.OrderItem;
import com.ecom.cartora.order.entity.OrderShippingAddress;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.repository.OrderItemRepo;
import com.ecom.cartora.order.repository.OrderRepo;
import com.ecom.cartora.payment.PaymentRepo;
import com.ecom.cartora.payment.entity.PaymentStatus;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductRepo;
import com.ecom.cartora.product.ProductInventoryService;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {

        private final OrderRepo orderRepo;
        private final OrderItemRepo orderItemRepo;
        private final UserRepo userRepo;
        private final CartRepo cartRepo;
        private final CartItemRepo cartItemRepo;
        private final CouponService couponService;
        private final ProductInventoryService productInventoryService;
        private final PaymentRepo paymentRepo;

        public OrderService(OrderRepo orderRepo, OrderItemRepo orderItemRepo, UserRepo userRepo,
                CartRepo cartRepo, CartItemRepo cartItemRepo, CouponService couponService,
                ProductInventoryService productInventoryService, PaymentRepo paymentRepo) {
                this.orderRepo = orderRepo;
                this.orderItemRepo = orderItemRepo;
                this.userRepo = userRepo;
                this.cartRepo = cartRepo;
                this.cartItemRepo = cartItemRepo;
                this.couponService = couponService;
                this.productInventoryService = productInventoryService;
                this.paymentRepo = paymentRepo;
        }

        private User getUser() {

                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

                String username = authentication.getName();

                return userRepo.findByEmail(username)
                                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
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

                List<OrderItem> items = orderItemRepo.findByOrderId(order.getId());

                List<OrderItemResponse> itemResponses = new ArrayList<>();

                for (OrderItem item : items) {
                        itemResponses.add(
                                        buildOrderItemResponse(item));
                }

                OrderResponse response = new OrderResponse();

                response.setOrderId(order.getId());
                response.setTotalAmount(order.getTotalAmount());
                response.setStatus(order.getStatus());
                response.setOrderItems(itemResponses);
                response.setCreatedAt(order.getCreatedAt());
                response.setDiscountAmount(order.getDiscountAmount() == null
                                ? BigDecimal.ZERO
                                : order.getDiscountAmount());
                response.setCouponCode(order.getCouponCode());
                if (order.getShippingAddress() != null) {
                        OrderShippingAddress address = order.getShippingAddress();
                        response.setShippingAddress(new ShippingAddressRequest(
                                        address.getRecipientName(),
                                        address.getHouseNumberOrBuildingNumber(),
                                        address.getLocalityOrLandmark(),
                                        address.getCity(),
                                        address.getState(),
                                        address.getPostalCode(),
                                        address.getCountry()));
                }

                return response;
        }

        @Transactional
        public OrderResponse createOrder() {
                return createOrder(null);
        }

        @Transactional
        public OrderResponse createOrder(String couponCode) {
                return createOrder(couponCode, null);
        }

        @Transactional
        public OrderResponse createOrder(String couponCode, ShippingAddressRequest shippingAddress) {

                User user = getUser();

                Cart cart = cartRepo.findByUserIdForUpdate(user.getId())
                                .orElseThrow(() -> new CartNotFoundException("Cart Not Found"));

                List<CartItem> cartItems = cartItemRepo.findByCartId(cart.getId());

                if (cartItems.isEmpty()) {
                        throw new CartIsEmptyException("Cart Is Empty");
                }

                Map<Long, Product> lockedProducts = productInventoryService.lockAndRefresh(cartItems.stream()
                                .map(item -> item.getProduct().getId()).toList());

                for (CartItem item : cartItems) {
                        Product product = lockedProducts.get(item.getProduct().getId());
                        if (item.getQuantity() > product.getStockQuantity()) {
                                throw new InsufficientStockException(
                                                "Required quantity is not available for product: " + product.getName());
                        }
                }

                BigDecimal totalPrice = BigDecimal.ZERO;

                // Calculate total first
                for (CartItem item : cartItems) {

                        Product product = item.getProduct();

                        BigDecimal subtotal = product.getPrice()
                                        .multiply(
                                                        BigDecimal.valueOf(item.getQuantity()));

                        totalPrice = totalPrice.add(subtotal);
                }

                CouponDiscount couponDiscount = couponService.calculateDiscount(couponCode, totalPrice);
                BigDecimal finalAmount = totalPrice.subtract(couponDiscount.amount());

                Order order = new Order();

                order.setUser(user);
                order.setTotalAmount(finalAmount);
                order.setDiscountAmount(couponDiscount.amount());
                order.setCouponCode(couponDiscount.code());
                order.setInventoryReserved(true);
                if (shippingAddress != null) {
                        order.setShippingAddress(new OrderShippingAddress(
                                        shippingAddress.getRecipientName(),
                                        shippingAddress.getHouseNumberOrBuildingNumber(),
                                        shippingAddress.getLocalityOrLandmark(),
                                        shippingAddress.getCity(),
                                        shippingAddress.getState(),
                                        shippingAddress.getPostalCode(),
                                        shippingAddress.getCountry()));
                }
                order.setStatus(OrderStatus.PENDING);

                LocalDateTime now = LocalDateTime.now();

                order.setCreatedAt(now);
                order.setUpdatedAt(now);

                orderRepo.save(order);

                // Create order items as price snapshots
                for (CartItem item : cartItems) {

                        Product product = lockedProducts.get(item.getProduct().getId());
                        product.setStockQuantity(product.getStockQuantity() - item.getQuantity());

                        OrderItem orderItem = new OrderItem();

                        orderItem.setOrder(order);
                        orderItem.setProduct(product);
                        orderItem.setQuantity(item.getQuantity());

                        BigDecimal price = product.getPrice();

                        orderItem.setPrice(price);

                        BigDecimal subtotal = price.multiply(
                                        BigDecimal.valueOf(item.getQuantity()));

                        orderItem.setSubtotal(subtotal);

                        orderItemRepo.save(orderItem);
                }

                cartItemRepo.deleteByCartId(cart.getId());
                cart.setUpdatedAt(now);
                cartRepo.save(cart);

                return buildOrderResponse(order);
        }

        @Transactional(readOnly = true)
        public List<OrderResponse> getMyOrders() {

                User user = getUser();

                List<Order> orders = orderRepo.findByUserId(user.getId());

                List<OrderResponse> responses = new ArrayList<>();

                for (Order order : orders) {
                        responses.add(
                                        buildOrderResponse(order));
                }

                return responses;
        }

        @Transactional(readOnly = true)
        public List<OrderResponse> getAllOrders() {
                return orderRepo.findAllByOrderByCreatedAtDesc()
                                .stream()
                                .map(this::buildOrderResponse)
                                .toList();
        }

        @Transactional
        public OrderResponse updateOrderStatus(Long orderId, OrderStatus nextStatus) {
                Order order = orderRepo.findByIdForUpdate(orderId)
                                .orElseThrow(() -> new OrderNotFoundException("Order Doesn't Exist"));

                boolean validTransition = (order.getStatus() == OrderStatus.CONFIRMED
                                && nextStatus == OrderStatus.SHIPPED)
                                || (order.getStatus() == OrderStatus.SHIPPED
                                                && nextStatus == OrderStatus.DELIVERED);
                if (!validTransition) {
                        throw new OrderCancellationException(
                                        "Invalid order status transition: "
                                                        + order.getStatus() + " to " + nextStatus);
                }

                order.setStatus(nextStatus);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepo.save(order);
                return buildOrderResponse(order);
        }

        @Transactional(readOnly = true)
        public OrderResponse getOrderById(Long orderId) {

                User user = getUser();

                Order order = orderRepo.findById(orderId)
                                .orElseThrow(() -> new OrderNotFoundException(
                                                "Order Doesn't Exist"));

                if (!user.getId().equals(order.getUser().getId())) {
                        throw new UserOrderNotFoundException(
                                        "Order does not belong to the authenticated user");
                }

                return buildOrderResponse(order);
        }

        @Transactional
        public OrderResponse cancelOrder(Long orderId) {

                User user = getUser();

                Order order = orderRepo.findByIdForUpdate(orderId)
                                .orElseThrow(() -> new OrderNotFoundException(
                                                "Order Doesn't Exist"));

                if (!user.getId().equals(order.getUser().getId())) {
                        throw new UserOrderNotFoundException(
                                        "Order does not belong to the authenticated user");
                }

                if (order.getStatus() != OrderStatus.PENDING) {
                        throw new OrderCancellationException(
                                        order.getStatus()
                                                        + " Order Cannot Be Cancelled");
                }

                if (paymentRepo.findByOrderIdForUpdate(orderId)
                                .map(payment -> payment.getStatus() == PaymentStatus.SUCCESS
                                                || payment.getStatus() == PaymentStatus.REFUND_PENDING
                                                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                                                || payment.getStatus() == PaymentStatus.REFUNDED)
                                .orElse(false)) {
                        throw new OrderCancellationException("A paid order cannot be cancelled through this endpoint");
                }

                if (order.isInventoryReserved()) {
                        List<OrderItem> items = orderItemRepo.findByOrderId(orderId);
                        Map<Long, Product> products = productInventoryService.lockAndRefresh(items.stream()
                                        .map(item -> item.getProduct().getId()).toList());
                        items.forEach(item -> {
                                Product product = products.get(item.getProduct().getId());
                                product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
                        });
                        order.setInventoryReserved(false);
                }

                order.setStatus(OrderStatus.CANCELLED);
                order.setUpdatedAt(LocalDateTime.now());

                orderRepo.save(order);

                return buildOrderResponse(order);
        }
}
