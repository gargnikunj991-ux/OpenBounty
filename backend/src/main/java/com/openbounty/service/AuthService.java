package com.openbounty.service;

import com.openbounty.dto.request.auth.LoginRequest;
import com.openbounty.dto.request.auth.RefreshTokenRequest;
import com.openbounty.dto.request.auth.RegisterRequest;
import com.openbounty.dto.response.auth.AuthResponse;
import com.openbounty.dto.response.auth.TokenRefreshResponse;
import com.openbounty.dto.response.auth.UserProfileResponse;
import com.openbounty.dto.response.auth.UserSummaryResponse;
import com.openbounty.enums.Role;
import com.openbounty.exception.BadRequestException;
import com.openbounty.exception.DuplicateResourceException;
import com.openbounty.exception.ResourceNotFoundException;
import com.openbounty.exception.UnauthorizedException;
import com.openbounty.model.RefreshToken;
import com.openbounty.model.User;
import com.openbounty.repository.UserRepository;
import com.openbounty.security.JwtService;
import com.openbounty.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing user authentication, registration, refresh token rotation, and profile retrieval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    /**
     * Register a new user account with hashed password storage.
     * Direct registration as ROLE_ADMIN is forbidden.
     */
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        if (request.getRole() == Role.ROLE_ADMIN) {
            throw new BadRequestException("Direct registration as ROLE_ADMIN is not permitted. Only ROLE_CLIENT and ROLE_DEVELOPER are allowed.");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .reputationScore(0)
                .build();

        User savedUser = userRepository.saveAndFlush(user);
        log.info("Registered new user with email '{}' and role '{}'", savedUser.getEmail(), savedUser.getRole());

        return UserProfileResponse.from(savedUser);
    }

    /**
     * Authenticate user credentials and issue signed JWT access token alongside a database-backed refresh token.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );
        } catch (AuthenticationException ex) {
            log.warn("Authentication failed for email '{}': {}", normalizedEmail, ex.getMessage());
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        String accessToken = jwtService.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        log.info("Successfully authenticated user '{}'", user.getEmail());

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken.getToken())
                .type("Bearer")
                .expiresInMs(jwtService.getExpirationMs())
                .user(UserSummaryResponse.from(user))
                .build();
    }

    /**
     * Exchange a valid refresh token for a newly issued access token and rotated refresh token.
     */
    @Transactional
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(request.getRefreshToken());
        User user = newRefreshToken.getUser();
        String newAccessToken = jwtService.generateToken(user);

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .tokenType("Bearer")
                .expiresInMs(jwtService.getExpirationMs())
                .build();
    }

    /**
     * Logout user by revoking the active refresh token and/or terminating user sessions.
     */
    @Transactional
    public void logout(RefreshTokenRequest request, UserPrincipal currentUser) {
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            refreshTokenService.revokeToken(request.getRefreshToken());
        }
        if (currentUser != null) {
            userRepository.findById(currentUser.getId()).ifPresent(refreshTokenService::revokeAllForUser);
        }
    }

    /**
     * Retrieve the current authenticated user's detailed profile.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));

        return UserProfileResponse.from(user);
    }

    /**
     * Retrieve the current authenticated User domain entity for internal service transactions.
     */
    @Transactional(readOnly = true)
    public User getCurrentUserEntity(UserPrincipal userPrincipal) {
        if (userPrincipal == null) {
            throw new UnauthorizedException("Full authentication is required to access this resource");
        }

        return userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
    }
}
