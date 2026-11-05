package com.ecom.cartora.order.repository;

import com.ecom.cartora.order.entity.OrderItem;
import com.ecom.cartora.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Collection;

@Repository
public interface OrderItemRepo extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long id);

    List<OrderItem> findByProduct_IdAndOrder_User_IdAndOrder_StatusIn(
            Long productId,
            Long userId,
            Collection<OrderStatus> statuses);
}
