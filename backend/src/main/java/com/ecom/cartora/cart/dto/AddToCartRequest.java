package com.ecom.cartora.cart.dto;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddToCartRequest {
    @NotNull(message = "Product Id is required")
    private Long productId;
    @Positive(message = "Quantity must be greater than zero")
    private int quantity;

}
