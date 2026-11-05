package com.ecom.cartora.payment;

import com.ecom.cartora.payment.entity.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class SimulatedPaymentProcessor {

    public boolean process(PaymentMethod method) {
        return ThreadLocalRandom.current().nextDouble() < 0.8;
    }
}