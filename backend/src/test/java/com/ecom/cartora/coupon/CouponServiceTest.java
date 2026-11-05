package com.ecom.cartora.coupon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepo couponRepo;

    @InjectMocks
    private CouponService couponService;

    @Test
    void calculateDiscount_shouldApplyPercentageAndMaximumCap() {
        // Arrange
        Coupon coupon = coupon(
                "SAVE20", DiscountType.PERCENTAGE,
                "20", "100", "50");
        when(couponRepo.findByCodeIgnoreCase("SAVE20"))
                .thenReturn(Optional.of(coupon));

        // Act
        CouponDiscount discount = couponService.calculateDiscount(
                " save20 ", new BigDecimal("300.00"));

        // Assert
        assertThat(discount.code()).isEqualTo("SAVE20");
        assertThat(discount.amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void calculateDiscount_shouldNeverExceedSubtotal_forFixedCoupon() {
        // Arrange
        Coupon coupon = coupon(
                "FLAT75", DiscountType.FIXED_AMOUNT,
                "75", "0", null);
        when(couponRepo.findByCodeIgnoreCase("FLAT75"))
                .thenReturn(Optional.of(coupon));

        // Act
        CouponDiscount discount = couponService.calculateDiscount(
                "FLAT75", new BigDecimal("50.00"));

        // Assert
        assertThat(discount.amount()).isEqualByComparingTo("50.00");
    }

    @Test
    void calculateDiscount_shouldRejectExpiredCoupon() {
        // Arrange
        Coupon coupon = coupon(
                "OLDDEAL", DiscountType.FIXED_AMOUNT,
                "10", "0", null);
        coupon.setValidUntil(LocalDateTime.now().minusDays(1));
        when(couponRepo.findByCodeIgnoreCase("OLDDEAL"))
                .thenReturn(Optional.of(coupon));

        // Act + Assert
        assertThatThrownBy(() -> couponService.calculateDiscount(
                "OLDDEAL", new BigDecimal("100.00")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private Coupon coupon(
            String code,
            DiscountType type,
            String value,
            String minimum,
            String maximum) {
        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDiscountType(type);
        coupon.setDiscountValue(new BigDecimal(value));
        coupon.setMinimumOrderAmount(new BigDecimal(minimum));
        coupon.setMaximumDiscountAmount(maximum == null ? null : new BigDecimal(maximum));
        coupon.setActive(true);
        return coupon;
    }
}