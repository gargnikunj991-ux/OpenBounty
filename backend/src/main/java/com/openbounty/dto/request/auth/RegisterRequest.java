package com.openbounty.dto.request.auth;

import com.openbounty.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for creating a new user account (Client or Developer).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "User registration payload")
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Schema(description = "Full name of the user", example = "Alex Johnson")
    private String name;

    @NotBlank(message = "Email address is required")
    @Email(message = "Email must be a valid email address format")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    @Schema(description = "Unique email address for authentication", example = "alex.johnson@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
    @Schema(description = "Account password (min 8 characters)", example = "SecurePassword123!")
    private String password;

    @NotNull(message = "Role is required (ROLE_CLIENT or ROLE_DEVELOPER)")
    @Schema(description = "Platform role of the user", example = "ROLE_DEVELOPER")
    private Role role;
}
