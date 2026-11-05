package com.ecom.cartora.payment;

import com.ecom.cartora.cart.entity.Cart;

import com.ecom.cartora.cart.repository.CartItemRepo;
import com.ecom.cartora.cart.repository.CartRepo;
import com.ecom.cartora.exception.InsufficientStockException;
import com.ecom.cartora.exception.OrderNotFoundException;
import com.ecom.cartora.exception.PaymentCannotBeDone;
import com.ecom.cartora.exception.UserOrderNotFoundException;
import com.ecom.cartora.order.entity.Order;
import com.ecom.cartora.order.OrderPaidEvent;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.entity.OrderItem;
import com.ecom.cartora.order.repository.OrderItemRepo;
import com.ecom.cartora.order.repository.OrderRepo;
import com.ecom.cartora.payment.dto.PaymentRequest;
import com.ecom.cartora.payment.dto.PaymentResponse;
import com.ecom.cartora.payment.SimulatedPaymentProcessor;
import com.ecom.cartora.payment.entity.Payment;
import com.ecom.cartora.payment.entity.PaymentMethod;
import com.ecom.cartora.payment.entity.PaymentStatus;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductRepo;
import com.ecom.cartora.product.ProductInventoryService;
import com.ecom.cartora.user.User;
import com.ecom.cartora.user.UserRepo;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepo paymentRepo;
    private final UserRepo userRepo;
    private final OrderRepo orderRepo;
    private final OrderItemRepo orderItemRepo;
    private final CartRepo cartRepo;
    private final CartItemRepo cartItemRepo;
    private final SimulatedPaymentProcessor simulatedPaymentProcessor;
    private final ProductInventoryService productInventoryService;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(PaymentRepo paymentRepo, UserRepo userRepo, OrderRepo orderRepo,
            OrderItemRepo orderItemRepo, CartRepo cartRepo, CartItemRepo cartItemRepo,
            SimulatedPaymentProcessor simulatedPaymentProcessor, ProductInventoryService productInventoryService,
            ApplicationEventPublisher eventPublisher) {
        this.paymentRepo = paymentRepo;
        this.userRepo = userRepo;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.cartRepo = cartRepo;
        this.cartItemRepo = cartItemRepo;
        this.simulatedPaymentProcessor = simulatedPaymentProcessor;
        this.productInventoryService = productInventoryService;
        this.eventPublisher = eventPublisher;
    }

    private User getUser() {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();

        return userRepo.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private PaymentResponse buildPaymentResponse(Payment payment) {

        PaymentResponse response = new PaymentResponse();

        response.setPaymentId(payment.getId());
        response.setOrderId(payment.getOrder().getId());
        response.setAmount(payment.getAmount());
        response.setStatus(payment.getStatus());
        response.setPaymentMethod(payment.getPaymentMethod());
        response.setTransactionId(payment.getTransactionId());
        response.setCreatedAt(payment.getCreatedAt());
        response.setProviderOrderId(payment.getProviderOrderId());
        response.setProviderPaymentId(payment.getProviderPaymentId());
        response.setFailureReason(payment.getFailureReason());

        return response;
    }

    @Transactional
    public PaymentResponse makePayment(PaymentRequest request) {

        User user = getUser();

        Order order = orderRepo.findByIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException("Order Doesn't Exist"));

        // Verify order ownership
        if (!user.getId().equals(order.getUser().getId())) {

            throw new UserOrderNotFoundException(
                    "Order does not belong to the authenticated user");
        }

        // Only PENDING orders can be paid
        if (order.getStatus() != OrderStatus.PENDING) {

            throw new PaymentCannotBeDone(
                    "Order is " + order.getStatus()
                            + ", so payment can't be done");
        }

        /*
         * Check whether a payment already exists.
         *
         * SUCCESS -> cannot pay again
         * PENDING -> payment already in progress
         * FAILED -> retry is allowed
         */
        Payment payment = paymentRepo.findByOrderIdForUpdate(order.getId())
                .orElse(null);

        if (payment != null) {

            if (payment.getStatus() == PaymentStatus.SUCCESS) {

                throw new PaymentCannotBeDone(
                        "Payment has already been completed for this order");
            }

            if (payment.getStatus() == PaymentStatus.PENDING) {

                throw new PaymentCannotBeDone(
                        "Payment is already in progress for this order");
            }

            if (payment.getStatus() != PaymentStatus.FAILED) {
                throw new PaymentCannotBeDone("This payment cannot be retried in its current state");
            }
        }

        // Amount always comes from the Order
        BigDecimal amount = order.getTotalAmount();

        /*
         * If previous payment FAILED,
         * reuse the same payment record.
         *
         * Otherwise create a new payment.
         */
        if (payment == null) {

            payment = new Payment();

            payment.setOrder(order);
            payment.setCreatedAt(LocalDateTime.now());
            payment.setAttemptNumber(1);
        } else {
            payment.setAttemptNumber(payment.getAttemptNumber() + 1);
        }

        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setTransactionId(UUID.randomUUID().toString());
        payment.setUpdatedAt(LocalDateTime.now());

        paymentRepo.save(payment);

        // Simulate payment gateway
        boolean paymentSuccessful = simulatedPaymentProcessor.process(request.getPaymentMethod());

        if (paymentSuccessful) {

            List<OrderItem> orderItems = orderItemRepo.findByOrderId(order.getId());
            if (!order.isInventoryReserved()) {
                // Support pending orders created before inventory reservations were
                // introduced. New orders already deducted stock at order creation.
                Map<Long, Product> lockedProducts = productInventoryService.lockAndRefresh(orderItems.stream()
                        .map(item -> item.getProduct().getId()).toList());

                for (OrderItem orderItem : orderItems) {
                    Product product = lockedProducts.get(orderItem.getProduct().getId());
                    if (orderItem.getQuantity() > product.getStockQuantity()) {
                        payment.setStatus(PaymentStatus.FAILED);
                        payment.setUpdatedAt(LocalDateTime.now());
                        paymentRepo.save(payment);
                        return buildPaymentResponse(payment);
                    }
                }

                for (OrderItem orderItem : orderItems) {
                    Product product = lockedProducts.get(orderItem.getProduct().getId());
                    product.setStockQuantity(product.getStockQuantity() - orderItem.getQuantity());
                }
            }

            // Payment successful
            payment.setStatus(PaymentStatus.SUCCESS);
            order.setInventoryReserved(false);

            order.setStatus(OrderStatus.CONFIRMED);
            order.setUpdatedAt(LocalDateTime.now());
            eventPublisher.publishEvent(new OrderPaidEvent(user.getEmail(), user.getName(), order.getId(), amount));

            // Clear cart
            Cart cart = cartRepo.findByUserId(user.getId())
                    .orElse(null);

            if (cart != null) {

                cartItemRepo.deleteByCartId(cart.getId());

                cart.setUpdatedAt(LocalDateTime.now());

                cartRepo.save(cart);
            }

        } else {

            // Payment failed
            payment.setStatus(PaymentStatus.FAILED);
        }

        payment.setUpdatedAt(LocalDateTime.now());

        paymentRepo.save(payment);

        orderRepo.save(order);

        return buildPaymentResponse(payment);
    }
}
