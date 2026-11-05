package com.ecom.cartora.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OrderReservationExpirationScheduler {
    private final OrderReservationExpirationService expirationService;
    private final long reservationMinutes;

    public OrderReservationExpirationScheduler(OrderReservationExpirationService expirationService,
            @Value("${app.orders.reservation-minutes:30}") long reservationMinutes) {
        this.expirationService = expirationService;
        this.reservationMinutes = reservationMinutes;
    }

    @Scheduled(fixedDelayString = "${app.orders.reservation-scan-ms:60000}")
    public void expireStaleReservations() {
        var expiredOrderIds = expirationService.findExpiredReservationIds(
                LocalDateTime.now().minusMinutes(reservationMinutes));
        expiredOrderIds.forEach(expirationService::expireReservation);
    }
}
