package com.ecom.cartora.order;

import java.math.BigDecimal;

public record OrderPaidEvent(String recipient, String customerName, Long orderId, BigDecimal totalAmount) {
}
