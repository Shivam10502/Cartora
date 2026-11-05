package com.ecom.cartora.order.repository;

import com.ecom.cartora.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.Collection;
import java.time.LocalDate;
import com.ecom.cartora.order.entity.OrderStatus;

@Repository
public interface OrderRepo extends JpaRepository<Order, Long> {
    List<Order> findByUserId(Long userId);

    List<Order> findAllByOrderByCreatedAtDesc();

    @Query("select o.id from Order o where o.status = com.ecom.cartora.order.entity.OrderStatus.PENDING "
            + "and o.inventoryReserved = true and o.createdAt < :cutoff order by o.createdAt, o.id")
    List<Long> findExpiredReservationOrderIds(@Param("cutoff") java.time.LocalDateTime cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    long countByStatus(OrderStatus status);

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.status in :statuses")
    BigDecimal sumRevenueByStatuses(@Param("statuses") Collection<OrderStatus> statuses);

    interface DailyRevenueProjection {
        LocalDate getSaleDate();
        BigDecimal getRevenue();
    }

    @Query(value = "select cast(created_at as date) as saleDate, sum(total_amount) as revenue "
            + "from orders where status in ('CONFIRMED','SHIPPED','DELIVERED') "
            + "and created_at >= :since group by cast(created_at as date) order by cast(created_at as date)",
            nativeQuery = true)
    List<DailyRevenueProjection> findDailyRevenueSince(@Param("since") java.time.LocalDateTime since);
}
