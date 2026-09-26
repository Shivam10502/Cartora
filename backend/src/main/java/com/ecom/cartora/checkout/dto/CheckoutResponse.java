package com.ecom.cartora.checkout.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CheckoutResponse {

    private ShippingAddressRequest shippingAddress;
    private List<CheckoutItemResponse> items;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
}