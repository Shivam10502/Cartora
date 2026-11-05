package com.ecom.cartora.payment;

import com.ecom.cartora.payment.dto.PaymentRequest;
import com.ecom.cartora.payment.dto.PaymentResponse;
import com.ecom.cartora.payment.dto.GatewayOrderRequest;
import com.ecom.cartora.payment.dto.PaymentProviderResponse;
import com.ecom.cartora.payment.dto.RazorpayOrderResponse;
import com.ecom.cartora.payment.dto.RazorpayVerificationRequest;
import com.ecom.cartora.exception.PaymentCannotBeDone;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final RazorpayPaymentService razorpayPaymentService;
    private final RazorpayProperties razorpayProperties;
    private final String provider;

    public PaymentController(
            PaymentService paymentService,
            RazorpayPaymentService razorpayPaymentService,
            RazorpayProperties razorpayProperties,
            @Value("${app.payment.provider:simulated}") String provider) {
        this.paymentService = paymentService;
        this.razorpayPaymentService = razorpayPaymentService;
        this.razorpayProperties = razorpayProperties;
        this.provider = provider;
    }

    @GetMapping("/provider")
    public PaymentProviderResponse getProvider() {
        return new PaymentProviderResponse(provider.equalsIgnoreCase("razorpay") ? "RAZORPAY" : "SIMULATED",
                provider.equalsIgnoreCase("razorpay") ? razorpayProperties.getKeyId() : null);
    }

    @PostMapping
    public PaymentResponse makePayment(
            @Valid @RequestBody PaymentRequest request) {
        if (provider.equalsIgnoreCase("razorpay")) {
            throw new PaymentCannotBeDone("Use the Razorpay checkout flow for this payment provider");
        }
        return paymentService.makePayment(request);
    }

    @PostMapping("/gateway-order")
    public RazorpayOrderResponse createGatewayOrder(@Valid @RequestBody GatewayOrderRequest request) {
        requireRazorpay();
        return razorpayPaymentService.createGatewayOrder(request.orderId());
    }

    @PostMapping("/verify")
    public PaymentResponse verifyPayment(@Valid @RequestBody RazorpayVerificationRequest request) {
        requireRazorpay();
        return razorpayPaymentService.verifyPayment(request);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "x-razorpay-event-id", required = false) String eventId,
            @RequestHeader("x-razorpay-signature") String signature,
            @RequestBody byte[] rawBody) {
        requireRazorpay();
        razorpayPaymentService.handleWebhook(eventId, signature, rawBody);
        return ResponseEntity.ok().build();
    }

    private void requireRazorpay() {
        if (!provider.equalsIgnoreCase("razorpay")) {
            throw new PaymentCannotBeDone("Razorpay is not the configured payment provider");
        }
    }
}
