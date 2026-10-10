package com.ecom.cartora.payment;

import com.ecom.cartora.payment.dto.PaymentRequest;
import com.ecom.cartora.payment.dto.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping
    public PaymentResponse makePayment(
            @Valid @RequestBody PaymentRequest request) {

        return paymentService.makePayment(request);
    }
}