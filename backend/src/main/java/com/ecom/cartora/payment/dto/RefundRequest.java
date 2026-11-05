package com.ecom.cartora.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {
    @DecimalMin(value = "0.01", message = "Refund amount must be positive")
    private BigDecimal amount;
}
