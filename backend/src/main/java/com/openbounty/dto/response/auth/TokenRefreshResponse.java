package com.openbounty.dto.response.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response payload returning newly issued JWT access token and rotated refresh token.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Token refresh response payload")
public class TokenRefreshResponse {

    @Schema(description = "Newly issued signed JWT access token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "Rotated opaque refresh token", example = "550e8400-e29b-41d4-a716-446655440000")
    private String refreshToken;

    @Builder.Default
    @Schema(description = "Token type", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Access token validity in milliseconds", example = "600000")
    private long expiresInMs;
}
