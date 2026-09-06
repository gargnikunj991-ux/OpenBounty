package com.openbounty.dto.request.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for user authentication and JWT token generation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "User login credentials")
public class LoginRequest {

    @NotBlank(message = "Email address is required")
    @Email(message = "Email must be a valid email address format")
    @Schema(description = "Registered email address", example = "alex.johnson@example.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password", example = "SecurePassword123!")
    private String password;
}
