package com.ecom.cartora.order;

import com.ecom.cartora.user.EmailNotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderEmailListener {
    private final EmailNotificationService emailService;

    public OrderEmailListener(EmailNotificationService emailService) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendOrderConfirmation(OrderPaidEvent event) {
        if (!emailService.isConfigured()) return;
        String body = "Hello " + event.customerName() + ",\n\n"
                + "Your Cartora order #" + event.orderId() + " is confirmed.\n"
                + "Paid total: INR " + event.totalAmount() + "\n\n"
                + "Thank you for shopping with Cartora.";
        emailService.send(event.recipient(), "Cartora order #" + event.orderId() + " confirmed", body);
    }
}
