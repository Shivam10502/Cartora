package com.ecom.cartora.exception;

public class PaymentCannotBeDone extends RuntimeException {

    public PaymentCannotBeDone(String message) {
        super(message);
    }
}