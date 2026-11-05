package com.ecom.cartora.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepo extends JpaRepository<PasswordResetToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash and t.expiresAt > :now")
    Optional<PasswordResetToken> findValidTokenForUpdate(
            @Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
    Optional<PasswordResetToken> findTopByUserIdOrderByCreatedAtDesc(Long userId);
    void deleteByUserId(Long userId);
    void deleteByExpiresAtBefore(LocalDateTime now);
}
