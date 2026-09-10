package com.openbounty.controller;

import com.openbounty.dto.request.auth.LoginRequest;
import com.openbounty.dto.request.auth.RefreshTokenRequest;
import com.openbounty.dto.request.auth.RegisterRequest;
import com.openbounty.dto.response.ErrorResponse;
import com.openbounty.dto.response.auth.AuthResponse;
import com.openbounty.dto.response.auth.TokenRefreshResponse;
import com.openbounty.dto.response.auth.UserProfileResponse;
import com.openbounty.security.UserPrincipal;
import com.openbounty.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller handling authentication, account registration, token refresh, logout, and profile retrieval.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, credential verification, refresh tokens, and session management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register new account", description = "Creates a new client or developer user account with hashed password storage.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created successfully",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure or invalid role",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email address already registered",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserProfileResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "User login & token generation", description = "Verifies user credentials and returns a signed JWT access token alongside a refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Exchanges a valid refresh token for a newly issued access token and rotated refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully",
                    content = @Content(schema = @Schema(implementation = TokenRefreshResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid, expired, or revoked refresh token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TokenRefreshResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        TokenRefreshResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Revokes the active refresh token and terminates the user session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged out successfully")
    })
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        authService.logout(request, currentUser);
        return ResponseEntity.ok(Map.of("message", "Successfully logged out. Refresh token revoked."));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated profile", description = "Fetches the profile and reputation score of the currently authenticated user.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully",
                    content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        UserProfileResponse response = authService.getCurrentUserProfile(userPrincipal);
        return ResponseEntity.ok(response);
    }
}
