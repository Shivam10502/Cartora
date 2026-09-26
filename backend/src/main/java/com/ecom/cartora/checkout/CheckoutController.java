package com.ecom.cartora.checkout;

import com.ecom.cartora.checkout.dto.CheckoutRequest;
import com.ecom.cartora.checkout.dto.CheckoutResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    @Autowired
    private CheckoutService checkoutService;

    @PostMapping
    public CheckoutResponse createCheckout(@Valid @RequestBody CheckoutRequest request){
        return checkoutService.createCheckout(request);
    }
}
