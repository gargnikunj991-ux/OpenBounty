package com.openbounty.service;

import com.openbounty.enums.Role;
import com.openbounty.exception.UnauthorizedException;
import com.openbounty.model.RefreshToken;
import com.openbounty.model.User;
import com.openbounty.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    private User sampleUser;
    private RefreshToken sampleToken;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, 604800000L);

        sampleUser = User.builder()
                .id(1L)
                .name("Alex Johnson")
                .email("alex@example.com")
                .role(Role.ROLE_DEVELOPER)
                .build();

        sampleToken = RefreshToken.builder()
                .id(10L)
                .user(sampleUser)
                .token("test-uuid-refresh-token")
                .expiryDate(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
    }

    @Test
    @DisplayName("createRefreshToken persists new token with future expiration")
    void testCreateRefreshToken_Success() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken created = refreshTokenService.createRefreshToken(sampleUser);

        assertThat(created).isNotNull();
        assertThat(created.getUser()).isEqualTo(sampleUser);
        assertThat(created.getToken()).isNotBlank();
        assertThat(created.getExpiryDate()).isAfter(Instant.now());
        assertThat(created.isRevoked()).isFalse();

        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("verifyExpiration succeeds for active valid token")
    void testVerifyExpiration_Valid() {
        RefreshToken verified = refreshTokenService.verifyExpiration(sampleToken);
        assertThat(verified).isEqualTo(sampleToken);
    }

    @Test
    @DisplayName("verifyExpiration throws UnauthorizedException for revoked token")
    void testVerifyExpiration_Revoked() {
        sampleToken.setRevoked(true);

        assertThatThrownBy(() -> refreshTokenService.verifyExpiration(sampleToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    @DisplayName("verifyExpiration deletes expired token and throws UnauthorizedException")
    void testVerifyExpiration_Expired() {
        sampleToken.setExpiryDate(Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> refreshTokenService.verifyExpiration(sampleToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");

        verify(refreshTokenRepository).delete(sampleToken);
    }

    @Test
    @DisplayName("rotateRefreshToken rotates valid token and issues a new one")
    void testRotateRefreshToken_Success() {
        when(refreshTokenRepository.findByToken("test-uuid-refresh-token")).thenReturn(Optional.of(sampleToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken rotated = refreshTokenService.rotateRefreshToken("test-uuid-refresh-token");

        assertThat(rotated).isNotNull();
        assertThat(rotated.getUser()).isEqualTo(sampleUser);
        assertThat(sampleToken.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("rotateRefreshToken detects reuse of revoked token, wipes sessions and alerts")
    void testRotateRefreshToken_ReuseDetection() {
        sampleToken.setRevoked(true);
        when(refreshTokenRepository.findByToken("test-uuid-refresh-token")).thenReturn(Optional.of(sampleToken));

        assertThatThrownBy(() -> refreshTokenService.rotateRefreshToken("test-uuid-refresh-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Security alert: Revoked refresh token reuse detected");

        verify(refreshTokenRepository).deleteByUser(sampleUser);
    }

    @Test
    @DisplayName("revokeToken marks token as revoked")
    void testRevokeToken() {
        when(refreshTokenRepository.findByToken("test-uuid-refresh-token")).thenReturn(Optional.of(sampleToken));

        refreshTokenService.revokeToken("test-uuid-refresh-token");

        assertThat(sampleToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(sampleToken);
    }

    @Test
    @DisplayName("revokeAllForUser deletes all tokens for user")
    void testRevokeAllForUser() {
        when(refreshTokenRepository.deleteByUser(sampleUser)).thenReturn(3);

        refreshTokenService.revokeAllForUser(sampleUser);

        verify(refreshTokenRepository).deleteByUser(sampleUser);
    }
}
