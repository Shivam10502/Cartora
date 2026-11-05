package com.ecom.cartora.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductReviewRequest {

    @Min(1)
    @Max(5)
    private int rating;

    @NotBlank
    @Size(max = 80)
    private String title;

    @NotBlank
    @Size(max = 1000)
    private String comment;
}