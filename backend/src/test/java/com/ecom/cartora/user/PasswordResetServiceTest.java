package com.ecom.cartora.user;

import com.ecom.cartora.user.dto.ResetPasswordRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock private UserRepo userRepo;
    @Mock private PasswordResetTokenRepo tokenRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ObjectProvider<EmailNotificationService> emailProvider;
    @Mock private EmailNotificationService emailService;

    @Test
    void requestResetStoresOnlyTokenHashAndEmailsOneTimeLink() {
        User user = new User();
        user.setId(42L);
        user.setEmail("customer@example.test");
        when(userRepo.findByEmailIgnoreCaseForUpdate("customer@example.test")).thenReturn(Optional.of(user));
        when(tokenRepo.findTopByUserIdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.empty());
        when(emailProvider.getIfAvailable()).thenReturn(emailService);
        when(emailService.isConfigured()).thenReturn(true);
        PasswordResetService service = new PasswordResetService(
                userRepo, tokenRepo, passwordEncoder, emailProvider, "https://shop.example.test");

        service.requestReset(" Customer@Example.Test ");

        ArgumentCaptor<PasswordResetToken> stored = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepo).save(stored.capture());
        assertThat(stored.getValue().getTokenHash()).hasSize(64);
        assertThat(stored.getValue().getExpiresAt()).isAfter(LocalDateTime.now());
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(anyString(), anyString(), body.capture());
        assertThat(body.getValue()).contains("https://shop.example.test/reset-password?token=");
        assertThat(body.getValue()).doesNotContain(stored.getValue().getTokenHash());
    }

    @Test
    void resetPasswordEncodesNewPasswordAndConsumesAllActiveTokens() throws Exception {
        String rawToken = "one-time-token";
        User user = new User();
        user.setId(42L);
        PasswordResetToken stored = new PasswordResetToken();
        stored.setUser(user);
        stored.setTokenHash(hash(rawToken));
        stored.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(tokenRepo.findValidTokenForUpdate(eq(stored.getTokenHash()), any(LocalDateTime.class)))
                .thenReturn(Optional.of(stored));
        when(passwordEncoder.encode("StrongPass1!")).thenReturn("encoded-password");
        PasswordResetService service = new PasswordResetService(
                userRepo, tokenRepo, passwordEncoder, emailProvider, "http://localhost:5173");

        service.resetPassword(new ResetPasswordRequest(rawToken, "StrongPass1!"));

        assertThat(user.getPassword()).isEqualTo("encoded-password");
        verify(userRepo).save(user);
        verify(tokenRepo).deleteByUserId(user.getId());
    }

    @Test
    void resetPasswordRejectsUnknownOrExpiredToken() {
        String rawToken = "expired-token";
        when(tokenRepo.findValidTokenForUpdate(anyString(), any()))
                .thenReturn(Optional.empty());
        PasswordResetService service = new PasswordResetService(
                userRepo, tokenRepo, passwordEncoder, emailProvider, "http://localhost:5173");

        assertThatThrownBy(() -> service.resetPassword(new ResetPasswordRequest(rawToken, "StrongPass1!")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("invalid or expired");
    }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
