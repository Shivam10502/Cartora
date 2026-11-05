package com.ecom.cartora.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService();
    private final UserDetails customer = User.withUsername("customer@example.test")
            .password("encoded")
            .roles("CUSTOMER")
            .build();

    @BeforeEach
    void configureSigningProperties() {
        ReflectionTestUtils.setField(jwtService, "secretKey",
                "UnitTestSigningSecretMustBeAtLeastThirtyTwoBytesLong");
        ReflectionTestUtils.setField(jwtService, "expirationTime", 60_000L);
    }

    @Test
    void tokenAuthenticatesOnlyItsSubject() {
        String token = jwtService.generateToken(customer);

        assertThat(jwtService.extractUsername(token)).isEqualTo(customer.getUsername());
        assertThat(jwtService.isTokenValid(token, customer)).isTrue();
        assertThat(jwtService.isTokenValid(token, User.withUsername("other@example.test")
                .password("encoded").roles("CUSTOMER").build())).isFalse();
    }

    @Test
    void tokenWithModifiedSignatureIsRejected() {
        String token = jwtService.generateToken(customer);
        int signatureStart = token.lastIndexOf('.') + 1;
        String tampered = token.substring(0, signatureStart)
                + (token.charAt(signatureStart) == 'a' ? 'b' : 'a')
                + token.substring(signatureStart + 1);

        assertThatThrownBy(() -> jwtService.extractUsername(tampered))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }
}
