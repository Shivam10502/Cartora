package com.ecom.cartora.coupon.dto;

import com.ecom.cartora.coupon.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponRequest {

    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "^[A-Za-z0-9_-]{3,30}$")
    private String code;

    @NotNull
    private DiscountType discountType;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal discountValue;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal minimumOrderAmount;

    @DecimalMin("0.01")
    private BigDecimal maximumDiscountAmount;

    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
}