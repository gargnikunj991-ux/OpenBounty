package com.openbounty.dto.request.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for refreshing access tokens or revoking sessions on logout.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Refresh token request payload")
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    @Schema(description = "The opaque refresh token received during authentication", example = "550e8400-e29b-41d4-a716-446655440000")
    private String refreshToken;

    @Schema(description = "Whether to revoke all active sessions across all devices on logout", example = "false")
    private boolean allDevices;

    public RefreshTokenRequest(String refreshToken) {
        this.refreshToken = refreshToken;
        this.allDevices = false;
    }
}
