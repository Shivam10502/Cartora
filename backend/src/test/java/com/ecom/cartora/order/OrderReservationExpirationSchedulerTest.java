package com.ecom.cartora.order;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderReservationExpirationSchedulerTest {
    @Test
    void schedulerExpiresEachCandidateThroughItsOwnTransactionalServiceCall() {
        OrderReservationExpirationService service = mock(OrderReservationExpirationService.class);
        when(service.findExpiredReservationIds(any())).thenReturn(List.of(18L, 19L));
        OrderReservationExpirationScheduler scheduler = new OrderReservationExpirationScheduler(service, 30);

        scheduler.expireStaleReservations();

        verify(service).expireReservation(18L);
        verify(service).expireReservation(19L);
    }
}
