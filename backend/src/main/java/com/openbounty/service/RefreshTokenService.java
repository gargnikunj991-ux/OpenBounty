package com.openbounty.service;

import com.openbounty.exception.UnauthorizedException;
import com.openbounty.model.RefreshToken;
import com.openbounty.model.User;
import com.openbounty.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service managing database-persisted refresh token issuance, rotation, expiration, and revocation.
 * Implements Refresh Token Rotation (RTR) with token reuse detection without needing Redis.
 */
@Service
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${jwt.refresh-expiration-ms:86400000}") long refreshExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /**
     * Create and persist a new cryptographically random refresh token for a user.
     */
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .revoked(false)
                .build();

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        log.debug("Issued new refresh token for user '{}'", user.getEmail());
        return saved;
    }

    /**
     * Verify whether a token is expired or revoked.
     */
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.isRevoked()) {
            throw new UnauthorizedException("Refresh token has been revoked. Please log in again.");
        }
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new UnauthorizedException("Refresh token has expired. Please log in again.");
        }
        return token;
    }

    /**
     * Rotates a refresh token: invalidates the old token and issues a new one.
     * Detects potential token compromise if a revoked token is reused.
     */
    @Transactional
    public RefreshToken rotateRefreshToken(String requestToken) {
        RefreshToken token = refreshTokenRepository.findByToken(requestToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token."));

        // Reuse detection: If a revoked token is re-submitted, compromise is suspected
        if (token.isRevoked()) {
            refreshTokenRepository.deleteByUser(token.getUser());
            log.warn("Revoked refresh token reuse attempt detected for user '{}'. All active sessions terminated.",
                    token.getUser().getEmail());
            throw new UnauthorizedException("Security alert: Revoked refresh token reuse detected. All active sessions terminated. Please log in again.");
        }

        verifyExpiration(token);

        // Revoke the old token
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        // Issue and return fresh rotated token
        return createRefreshToken(token.getUser());
    }

    /**
     * Revoke a single refresh token upon logout.
     */
    @Transactional
    public void revokeToken(String tokenString) {
        refreshTokenRepository.findByToken(tokenString).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Revoked refresh token for user '{}'", token.getUser().getEmail());
        });
    }

    /**
     * Revoke all active refresh tokens for a user (global logout).
     */
    @Transactional
    public void revokeAllForUser(User user) {
        int count = refreshTokenRepository.deleteByUser(user);
        log.info("Terminated {} active refresh token sessions for user '{}'", count, user.getEmail());
    }

    /**
     * Periodically purge expired refresh tokens from the database.
     * Runs daily at 03:00 AM UTC to keep table size lean and indexes performant.
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public int purgeExpiredTokens() {
        Instant now = Instant.now();
        int deletedCount = refreshTokenRepository.deleteAllExpiredSince(now);
        log.info("Purged {} expired refresh tokens", deletedCount);
        return deletedCount;
    }
}
