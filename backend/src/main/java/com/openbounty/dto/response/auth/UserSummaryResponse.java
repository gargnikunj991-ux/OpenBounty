package com.openbounty.dto.response.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.openbounty.enums.Role;
import com.openbounty.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Concise public profile summary of a user for embedding in challenge/proposal responses.
 * Protects user privacy and eliminates circular JSON serialization references.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Compact user profile summary")
public class UserSummaryResponse {

    @Schema(description = "User unique ID", example = "1")
    private Long id;

    @Schema(description = "Full name of the user", example = "Alex Johnson")
    private String name;

    @Schema(description = "Email address (omitted in public solver cards when not appropriate)", example = "alex.johnson@example.com")
    private String email;

    @Schema(description = "User role", example = "ROLE_DEVELOPER")
    private Role role;

    @Schema(description = "Reputation score earned from completed challenges", example = "25")
    private int reputationScore;

    public static UserSummaryResponse from(User user) {
        if (user == null) {
            return null;
        }
        return UserSummaryResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .reputationScore(user.getReputationScore())
                .build();
    }
}
