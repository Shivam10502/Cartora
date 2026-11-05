package com.ecom.cartora.review.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRatingSummary {

    private BigDecimal averageRating;
    private long reviewCount;
}