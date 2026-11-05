package com.ecom.cartora.coupon;

import java.math.BigDecimal;

public record CouponDiscount(String code, BigDecimal amount) {

    public static CouponDiscount none() {
        return new CouponDiscount(null, BigDecimal.ZERO.setScale(2));
    }
}