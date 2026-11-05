package com.ecom.cartora.payment;

import com.ecom.cartora.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepo extends JpaRepository<PaymentWebhookEvent, Long> {
    boolean existsByEventId(String eventId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            insert into payment_webhook_event (event_id, event_type, received_at)
            values (:eventId, :eventType, :receivedAt)
            on conflict (event_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@org.springframework.data.repository.query.Param("eventId") String eventId,
            @org.springframework.data.repository.query.Param("eventType") String eventType,
            @org.springframework.data.repository.query.Param("receivedAt") java.time.LocalDateTime receivedAt);
}
