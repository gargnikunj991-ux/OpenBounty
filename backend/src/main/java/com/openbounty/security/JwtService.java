package com.openbounty.security;

import com.openbounty.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service for issuing, parsing, and validating cryptographic JSON Web Tokens (JWT).
 * Uses HMAC-SHA256 signature algorithm.
 */
@Service
public class JwtService {

    private static final String ISSUER = "openbounty-api";

    @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secretKey;

    @Value("${jwt.expiration-ms:600000}")
    private long jwtExpirationMs;

    public JwtService() {
    }

    // Constructor for programmatic injection during testing
    public JwtService(String secretKey, long jwtExpirationMs) {
        this.secretKey = secretKey;
        this.jwtExpirationMs = jwtExpirationMs;
    }

    /**
     * Generate token with default user claims from a User entity.
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("role", user.getRole().name());
        claims.put("name", user.getName());
        return buildToken(claims, user.getEmail());
    }

    /**
     * Generate token from Spring Security UserDetails (or UserPrincipal).
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        if (userDetails instanceof UserPrincipal principal) {
            claims.put("userId", principal.getId());
            claims.put("role", principal.getRole().name());
            claims.put("name", principal.getName());
        }
        return buildToken(claims, userDetails.getUsername());
    }

    /**
     * Generate token with custom claims and subject.
     */
    public String generateToken(Map<String, Object> extraClaims, String subject) {
        return buildToken(extraClaims, subject);
    }

    /**
     * Build signed JWT access token.
     */
    private String buildToken(Map<String, Object> claims, String subject) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuer(ISSUER)
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extract email/username from JWT token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract user ID claim from JWT token.
     */
    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object userId = claims.get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    /**
     * Extract role claim from JWT token.
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * Extract token expiration date.
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extract specific claim using a custom resolver.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Verify whether a token matches user credentials and is not expired.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return (username != null && username.equals(userDetails.getUsername())) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Validate token signature and expiration without checking specific UserDetails.
     */
    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Check if token has expired.
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Parse all claims from signed token.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Retrieve or generate secret signing key.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secretKey);
        } catch (Exception e) {
            keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }
}
