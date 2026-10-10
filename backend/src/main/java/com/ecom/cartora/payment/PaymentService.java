package com.ecom.cartora.payment;

import com.ecom.cartora.cart.entity.Cart;

import com.ecom.cartora.cart.repository.CartItemRepo;
import com.ecom.cartora.cart.repository.CartRepo;
import com.ecom.cartora.exception.InsufficientStockException;
import com.ecom.cartora.exception.OrderNotFoundException;
import com.ecom.cartora.exception.PaymentCannotBeDone;
import com.ecom.cartora.exception.UserOrderNotFoundException;
import com.ecom.cartora.order.entity.Order;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.entity.OrderItem;
import com.ecom.cartora.order.repository.OrderItemRepo;
import com.ecom.cartora.order.repository.OrderRepo;
import com.ecom.cartora.payment.dto.PaymentRequest;
import com.ecom.cartora.payment.dto.PaymentResponse;
import com.ecom.cartora.payment.entity.Payment;
import com.ecom.cartora.payment.entity.PaymentMethod;
import com.ecom.cartora.payment.entity.PaymentStatus;
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
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    @Autowired
    private PaymentRepo paymentRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private OrderRepo orderRepo;

    @Autowired
    private OrderItemRepo orderItemRepo;

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


    private boolean simulatePayment(PaymentMethod method) {

        return Math.random() < 0.8;
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

        return response;
    }


    @Transactional
    public PaymentResponse makePayment(PaymentRequest request) {

        User user = getUser();

        Order order = orderRepo.findById(request.getOrderId())
                .orElseThrow(() ->
                        new OrderNotFoundException("Order Doesn't Exist"));


        // Verify order ownership
        if (!user.getId().equals(order.getUser().getId())) {

            throw new UserOrderNotFoundException(
                    "Order does not belong to the authenticated user"
            );
        }


        // Only PENDING orders can be paid
        if (order.getStatus() != OrderStatus.PENDING) {

            throw new PaymentCannotBeDone(
                    "Order is " + order.getStatus()
                            + ", so payment can't be done"
            );
        }


        /*
         * Check whether a payment already exists.
         *
         * SUCCESS  -> cannot pay again
         * PENDING  -> payment already in progress
         * FAILED   -> retry is allowed
         */
        Payment payment = paymentRepo.findByOrderId(order.getId())
                .orElse(null);


        if (payment != null) {

            if (payment.getStatus() == PaymentStatus.SUCCESS) {

                throw new PaymentCannotBeDone(
                        "Payment has already been completed for this order"
                );
            }

            if (payment.getStatus() == PaymentStatus.PENDING) {

                throw new PaymentCannotBeDone(
                        "Payment is already in progress for this order"
                );
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
        }


        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setTransactionId(UUID.randomUUID().toString());
        payment.setUpdatedAt(LocalDateTime.now());

        paymentRepo.save(payment);


        // Simulate payment gateway
        boolean paymentSuccessful =
                simulatePayment(request.getPaymentMethod());


        if (paymentSuccessful) {

            /*
             * Before confirming payment,
             * verify stock again.
             */
            List<OrderItem> orderItems =
                    orderItemRepo.findByOrderId(order.getId());


            for (OrderItem orderItem : orderItems) {

                Product product = orderItem.getProduct();

                if (orderItem.getQuantity() >
                        product.getStockQuantity()) {

                    payment.setStatus(PaymentStatus.FAILED);
                    payment.setUpdatedAt(LocalDateTime.now());

                    paymentRepo.save(payment);

                    throw new InsufficientStockException(
                            "Insufficient stock for product: "
                                    + product.getName()
                    );
                }
            }


            // Deduct stock
            for (OrderItem orderItem : orderItems) {

                Product product = orderItem.getProduct();

                product.setStockQuantity(
                        product.getStockQuantity()
                                - orderItem.getQuantity()
                );
            }


            // Payment successful
            payment.setStatus(PaymentStatus.SUCCESS);

            order.setStatus(OrderStatus.CONFIRMED);
            order.setUpdatedAt(LocalDateTime.now());


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