package com.ecom.cartora.payment;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class RazorpaySignatureVerifierTest {

    @Test
    void matchesValidCheckoutSignature() throws Exception {
        String secret = "test_key_secret";
        String payload = "order_123|pay_456";
        String signature = sign(secret, payload);

        assertThat(RazorpaySignatureVerifier.matches(secret, payload, signature)).isTrue();
    }

    @Test
    void rejectsAlteredPayloadAndMalformedSignature() throws Exception {
        String secret = "test_key_secret";
        String signature = sign(secret, "order_123|pay_456");

        assertThat(RazorpaySignatureVerifier.matches(secret, "order_999|pay_456", signature)).isFalse();
        assertThat(RazorpaySignatureVerifier.matches(secret, "order_123|pay_456", "not-hex")).isFalse();
    }

    @Test
    void rejectsMissingSecret() {
        assertThat(RazorpaySignatureVerifier.matches("", "payload", "signature")).isFalse();
    }

    private String sign(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
