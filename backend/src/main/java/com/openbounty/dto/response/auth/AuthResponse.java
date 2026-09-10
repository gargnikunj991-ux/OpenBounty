package com.openbounty.dto.response.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Authentication response returning signed JWT access token and user metadata.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Authentication response payload containing JWT token")
public class AuthResponse {

    @Schema(description = "Signed JSON Web Token for Bearer Authorization header", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Builder.Default
    @Schema(description = "Authentication token type", example = "Bearer")
    private String type = "Bearer";

    @Schema(description = "Token validity lifetime in milliseconds", example = "600000")
    private long expiresInMs;

    @Schema(description = "Refresh token used to obtain a new access token when expired", example = "550e8400-e29b-41d4-a716-446655440000")
    private String refreshToken;

    @Schema(description = "Basic profile summary of the authenticated user")
    private UserSummaryResponse user;
}
