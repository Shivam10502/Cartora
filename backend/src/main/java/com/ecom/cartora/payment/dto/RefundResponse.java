package com.ecom.cartora.payment.dto;

import com.ecom.cartora.payment.entity.RefundStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundResponse(
        Long refundId,
        Long paymentId,
        Long orderId,
        String providerRefundId,
        String receipt,
        BigDecimal amount,
        RefundStatus status,
        LocalDateTime createdAt) {
}
