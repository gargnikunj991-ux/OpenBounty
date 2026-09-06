package com.openbounty.dto.request.milestone;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for defining a deliverable milestone within a proposal.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Milestone definition payload")
public class MilestoneCreateRequest {

    @NotBlank(message = "Milestone title is required")
    @Size(min = 3, max = 150, message = "Milestone title must be between 3 and 150 characters")
    @Schema(description = "Name/Title of this deliverable milestone", example = "Milestone 1: Filter Chain & JWT Token Generation")
    private String title;

    @Size(max = 2000, message = "Milestone description cannot exceed 2000 characters")
    @Schema(description = "Scope and verification criteria for this milestone", example = "Setup SecurityFilterChain and JwtService with RSA-256 / HMAC-SHA256 signature verification.")
    private String description;
}
