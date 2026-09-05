package com.openbounty.dto.response.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.openbounty.enums.Role;
import com.openbounty.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Detailed user profile response representation (for /api/auth/me or profile inspection).
 * Sensitive security credentials (password hashes) are strictly excluded.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detailed user profile response")
public class UserProfileResponse {

    @Schema(description = "Unique user identifier", example = "1")
    private Long id;

    @Schema(description = "Full name of the user", example = "Alex Johnson")
    private String name;

    @Schema(description = "Registered email address", example = "alex.johnson@example.com")
    private String email;

    @Schema(description = "User role", example = "ROLE_DEVELOPER")
    private Role role;

    @Schema(description = "Current reputation score", example = "25")
    private int reputationScore;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @Schema(description = "Account registration timestamp", example = "2026-08-31T20:45:00.000Z")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @Schema(description = "Last profile update timestamp", example = "2026-08-31T21:00:00.000Z")
    private LocalDateTime updatedAt;

    public static UserProfileResponse from(User user) {
        if (user == null) {
            return null;
        }
        return UserProfileResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .reputationScore(user.getReputationScore())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
