package com.ecom.cartora.payment.dto;

public record RazorpayOrderResponse(
        Long orderId,
        Long paymentId,
        String razorpayOrderId,
        String keyId,
        long amount,
        String currency) {
}
