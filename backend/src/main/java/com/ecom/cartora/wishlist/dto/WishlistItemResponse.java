package com.ecom.cartora.wishlist.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemResponse {

    private Long productId;
    private String productName;
    private String description;
    private BigDecimal price;
    private int stockQuantity;
    private LocalDateTime addedAt;
}