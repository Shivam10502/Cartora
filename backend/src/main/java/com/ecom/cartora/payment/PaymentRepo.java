package com.ecom.cartora.payment;

import com.ecom.cartora.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepo extends JpaRepository<Payment,Long> {

    Optional<Payment> findByOrderId(Long orderId);
    boolean existsByOrderId(Long orderId);

}
