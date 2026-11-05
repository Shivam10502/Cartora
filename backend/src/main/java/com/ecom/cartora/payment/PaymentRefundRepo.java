package com.ecom.cartora.payment;

import com.ecom.cartora.payment.entity.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.ecom.cartora.payment.entity.RefundStatus;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

public interface PaymentRefundRepo extends JpaRepository<PaymentRefund, Long> {
    List<PaymentRefund> findByPaymentIdOrderByCreatedAtDesc(Long paymentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRefund r where r.payment.id = :paymentId order by r.createdAt desc")
    List<PaymentRefund> findByPaymentIdOrderByCreatedAtDescForUpdate(@Param("paymentId") Long paymentId);
    Optional<PaymentRefund> findByProviderRefundId(String providerRefundId);
    Optional<PaymentRefund> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(sum(r.amount), 0) from PaymentRefund r where r.status = :status")
    BigDecimal sumRefundsByStatus(@Param("status") RefundStatus status);
}
