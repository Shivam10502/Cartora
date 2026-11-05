package com.ecom.cartora.user;

import com.ecom.cartora.user.dto.ResetPasswordRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class PasswordResetService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_TTL_MINUTES = 30;

    private final UserRepo userRepo;
    private final PasswordResetTokenRepo tokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<EmailNotificationService> emailProvider;
    private final String frontendBaseUrl;

    public PasswordResetService(UserRepo userRepo, PasswordResetTokenRepo tokenRepo,
            PasswordEncoder passwordEncoder, ObjectProvider<EmailNotificationService> emailProvider,
            @Value("${app.frontend.base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.emailProvider = emailProvider;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public void requestReset(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) return;
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        User user = userRepo.findByEmailIgnoreCaseForUpdate(email).orElse(null);
        EmailNotificationService emailService = emailProvider.getIfAvailable();
        if (user == null || emailService == null || !emailService.isConfigured()) return;

        LocalDateTime now = LocalDateTime.now();
        if (tokenRepo.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .filter(previous -> previous.getCreatedAt().isAfter(now.minusMinutes(1)))
                .isPresent()) return;

        tokenRepo.deleteByUserId(user.getId());
        tokenRepo.deleteByExpiresAtBefore(now);
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        tokenRepo.save(token(user, hash(token), now));
        String resetUrl = frontendBaseUrl.replaceAll("/$", "") + "/reset-password?token=" + token;
        emailService.send(user.getEmail(), "Reset your Cartora password",
                "Use this one-time link within 30 minutes to reset your password: " + resetUrl);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = hash(request.token());
        PasswordResetToken resetToken = tokenRepo.findValidTokenForUpdate(tokenHash, LocalDateTime.now())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Password reset link is invalid or expired"));
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepo.save(user);
        tokenRepo.deleteByUserId(user.getId());
    }

    private PasswordResetToken token(User user, String tokenHash, LocalDateTime now) {
        PasswordResetToken entity = new PasswordResetToken();
        entity.setUser(user);
        entity.setTokenHash(tokenHash);
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plusMinutes(TOKEN_TTL_MINUTES));
        return entity;
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
