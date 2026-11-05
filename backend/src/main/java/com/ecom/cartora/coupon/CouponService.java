package com.ecom.cartora.coupon;

import com.ecom.cartora.coupon.dto.CouponRequest;
import com.ecom.cartora.coupon.dto.CouponResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class CouponService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private final CouponRepo couponRepo;

    public CouponService(CouponRepo couponRepo) {
        this.couponRepo = couponRepo;
    }

    public CouponDiscount calculateDiscount(String rawCode, BigDecimal subtotal) {
        if (rawCode == null || rawCode.isBlank())
            return CouponDiscount.none();

        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        Coupon coupon = couponRepo.findByCodeIgnoreCase(code)
                .orElseThrow(() -> badRequest("Coupon code is invalid"));
        LocalDateTime now = LocalDateTime.now();
        if (!coupon.isActive()
                || (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom()))
                || (coupon.getValidUntil() != null && now.isAfter(coupon.getValidUntil()))) {
            throw badRequest("Coupon is not currently valid");
        }
        if (subtotal.compareTo(coupon.getMinimumOrderAmount()) < 0) {
            throw badRequest("Order does not meet the coupon minimum");
        }

        BigDecimal discount = coupon.getDiscountType() == DiscountType.PERCENTAGE
                ? subtotal.multiply(coupon.getDiscountValue())
                        .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP)
                : coupon.getDiscountValue().setScale(2, RoundingMode.HALF_UP);
        if (coupon.getMaximumDiscountAmount() != null) {
            discount = discount.min(coupon.getMaximumDiscountAmount());
        }
        discount = discount.min(subtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new CouponDiscount(coupon.getCode(), discount);
    }

    @Transactional
    public CouponResponse createCoupon(CouponRequest request) {
        String code = normalizeCode(request.getCode());
        if (couponRepo.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Coupon code already exists");
        }
        if (request.getValidFrom() != null && request.getValidUntil() != null
                && request.getValidUntil().isBefore(request.getValidFrom())) {
            throw badRequest("Coupon end time must be after its start time");
        }
        if (request.getDiscountType() == DiscountType.PERCENTAGE
                && request.getDiscountValue().compareTo(ONE_HUNDRED) > 0) {
            throw badRequest("Percentage discount cannot exceed 100");
        }

        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDiscountType(request.getDiscountType());
        coupon.setDiscountValue(request.getDiscountValue());
        coupon.setMinimumOrderAmount(request.getMinimumOrderAmount());
        coupon.setMaximumDiscountAmount(request.getMaximumDiscountAmount());
        coupon.setValidFrom(request.getValidFrom());
        coupon.setValidUntil(request.getValidUntil());
        coupon.setActive(true);
        return toResponse(couponRepo.save(coupon));
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> getCoupons() {
        return couponRepo.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public CouponResponse setCouponActive(Long id, boolean active) {
        Coupon coupon = couponRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Coupon not found"));
        coupon.setActive(active);
        return toResponse(couponRepo.save(coupon));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private CouponResponse toResponse(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(), coupon.getCode(), coupon.getDiscountType(),
                coupon.getDiscountValue(), coupon.getMinimumOrderAmount(),
                coupon.getMaximumDiscountAmount(), coupon.getValidFrom(),
                coupon.getValidUntil(), coupon.isActive());
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}