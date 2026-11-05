package com.ecom.cartora.order.dto;

import com.ecom.cartora.checkout.dto.ShippingAddressRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "Shipping address is required")
    @Valid
    private ShippingAddressRequest shippingAddress;

    @Size(max = 30)
    @Pattern(regexp = "^[A-Za-z0-9_-]*$", message = "Coupon code contains invalid characters")
    private String couponCode;
}
