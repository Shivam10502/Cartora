package com.ecom.cartora.payment.dto;

import jakarta.validation.constraints.NotNull;

public record GatewayOrderRequest(@NotNull Long orderId) {
}
