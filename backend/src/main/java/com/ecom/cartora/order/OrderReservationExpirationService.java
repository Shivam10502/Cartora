package com.ecom.cartora.order;

import com.ecom.cartora.order.entity.Order;
import com.ecom.cartora.order.entity.OrderItem;
import com.ecom.cartora.order.entity.OrderStatus;
import com.ecom.cartora.order.repository.OrderItemRepo;
import com.ecom.cartora.order.repository.OrderRepo;
import com.ecom.cartora.payment.PaymentRepo;
import com.ecom.cartora.payment.entity.PaymentStatus;
import com.ecom.cartora.product.Product;
import com.ecom.cartora.product.ProductInventoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class OrderReservationExpirationService {
    private static final int BATCH_SIZE = 100;

    private final OrderRepo orderRepo;
    private final OrderItemRepo orderItemRepo;
    private final PaymentRepo paymentRepo;
    private final ProductInventoryService inventoryService;

    public OrderReservationExpirationService(OrderRepo orderRepo, OrderItemRepo orderItemRepo,
            PaymentRepo paymentRepo, ProductInventoryService inventoryService) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.paymentRepo = paymentRepo;
        this.inventoryService = inventoryService;
    }

    public List<Long> findExpiredReservationIds(LocalDateTime cutoff) {
        return orderRepo.findExpiredReservationOrderIds(cutoff, PageRequest.of(0, BATCH_SIZE));
    }

    @Transactional
    public boolean expireReservation(Long orderId) {
        Order order = orderRepo.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING || !order.isInventoryReserved()) return false;

        var payment = paymentRepo.findByOrderIdForUpdate(orderId).orElse(null);
        if (payment != null && (payment.getStatus() == PaymentStatus.SUCCESS
                || payment.getStatus() == PaymentStatus.REFUND_PENDING
                || payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || payment.getStatus() == PaymentStatus.REFUNDED)) return false;

        List<OrderItem> items = orderItemRepo.findByOrderId(orderId);
        Map<Long, Product> products = inventoryService.lockAndRefresh(
                items.stream().map(item -> item.getProduct().getId()).toList());
        for (OrderItem item : items) {
            Product product = products.get(item.getProduct().getId());
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
        }

        LocalDateTime now = LocalDateTime.now();
        order.setInventoryReserved(false);
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(now);
        orderRepo.save(order);
        if (payment != null && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Inventory reservation expired before payment completed");
            payment.setUpdatedAt(now);
            paymentRepo.save(payment);
        }
        return true;
    }
}
