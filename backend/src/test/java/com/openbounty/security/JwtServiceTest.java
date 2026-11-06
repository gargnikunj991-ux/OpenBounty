package com.openbounty.security;

import com.openbounty.enums.Role;
import com.openbounty.model.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    private JwtService jwtService;
    private User testUser;
    private UserPrincipal testPrincipal;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, EXPIRATION_MS);

        testUser = User.builder()
                .id(42L)
                .name("Alex Johnson")
                .email("alex@example.com")
                .password("hashed_password")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(50)
                .build();

        testPrincipal = UserPrincipal.create(testUser);
    }

    @Test
    @DisplayName("generateToken from User populates subject and custom claims")
    void testGenerateTokenFromUser() {
        String token = jwtService.generateToken(testUser);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alex@example.com");
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.extractRole(token)).isEqualTo("ROLE_DEVELOPER");

        Claims claims = jwtService.extractAllClaims(token);
        assertThat(claims.getIssuer()).isEqualTo("openbounty-api");
        assertThat(claims.get("name", String.class)).isEqualTo("Alex Johnson");
    }

    @Test
    @DisplayName("generateToken from UserPrincipal populates claims identically")
    void testGenerateTokenFromUserPrincipal() {
        String token = jwtService.generateToken(testPrincipal);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("alex@example.com");
        assertThat(jwtService.extractUserId(token)).isEqualTo(42L);
        assertThat(jwtService.extractRole(token)).isEqualTo("ROLE_DEVELOPER");
    }

    @Test
    @DisplayName("isTokenValid returns true for valid token and matching user")
    void testIsTokenValid_Success() {
        String token = jwtService.generateToken(testPrincipal);

        boolean valid = jwtService.isTokenValid(token, testPrincipal);

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("isTokenValid returns false when username does not match")
    void testIsTokenValid_UsernameMismatch() {
        String token = jwtService.generateToken(testPrincipal);

        User anotherUser = User.builder()
                .id(99L)
                .name("Bob")
                .email("bob@example.com")
                .role(Role.ROLE_CLIENT)
                .build();
        UserPrincipal anotherPrincipal = UserPrincipal.create(anotherUser);

        boolean valid = jwtService.isTokenValid(token, anotherPrincipal);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("isTokenValid returns false for expired token")
    void testIsTokenValid_Expired() {
        // JwtService with negative expiration (already expired)
        JwtService expiredJwtService = new JwtService(TEST_SECRET, -1000);
        String token = expiredJwtService.generateToken(testPrincipal);

        boolean valid = jwtService.isTokenValid(token, testPrincipal);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("validateToken returns false for tampered token")
    void testValidateToken_Tampered() {
        String token = jwtService.generateToken(testPrincipal);
        String tamperedToken = token + "xyz";

        boolean valid = jwtService.validateToken(tamperedToken);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("extractExpiration returns valid future date")
    void testExtractExpiration() {
        String token = jwtService.generateToken(testPrincipal);
        Date expiration = jwtService.extractExpiration(token);

        assertThat(expiration).isAfter(new Date());
    }

    @Test
    @DisplayName("generateToken with extra claims sets custom properties")
    void testGenerateTokenWithExtraClaims() {
        String token = jwtService.generateToken(Map.of("customKey", "customValue"), "user@test.com");

        assertThat(jwtService.extractUsername(token)).isEqualTo("user@test.com");
        Claims claims = jwtService.extractAllClaims(token);
        assertThat(claims.get("customKey", String.class)).isEqualTo("customValue");
    }

    @Test
    @DisplayName("validateConfiguration succeeds with secure 256-bit key")
    void testValidateConfiguration_Success() {
        jwtService.validateConfiguration();
    }

    @Test
    @DisplayName("validateConfiguration rejects null or blank secret")
    void testValidateConfiguration_BlankSecret() {
        JwtService blankService = new JwtService("   ", EXPIRATION_MS);
        org.assertj.core.api.Assertions.assertThatThrownBy(blankService::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be configured");
    }

    @Test
    @DisplayName("validateConfiguration rejects secret with less than 256-bit entropy")
    void testValidateConfiguration_WeakSecret() {
        JwtService weakService = new JwtService("short-secret-12345", EXPIRATION_MS);
        org.assertj.core.api.Assertions.assertThatThrownBy(weakService::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 256 bits");
    }

    @Test
    @DisplayName("validateConfiguration rejects default insecure secret in production profile")
    void testValidateConfiguration_InsecureDefaultInProduction() {
        JwtService prodJwtService = new JwtService(JwtService.INSECURE_DEFAULT_SECRET, EXPIRATION_MS);
        org.springframework.mock.env.MockEnvironment prodEnv = new org.springframework.mock.env.MockEnvironment();
        prodEnv.setActiveProfiles("production");

        // Reflectively set mock environment
        org.springframework.test.util.ReflectionTestUtils.setField(prodJwtService, "environment", prodEnv);

        org.assertj.core.api.Assertions.assertThatThrownBy(prodJwtService::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRITICAL SECURITY ERROR: The default development JWT secret key cannot be used in production");
    }
}
