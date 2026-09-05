package com.openbounty.dto.request.milestone;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for submitting proof of completion for an assigned milestone.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Milestone deliverable submission payload")
public class MilestoneSubmitRequest {

    @NotBlank(message = "Deliverable URL is required")
    @Pattern(regexp = "^(https?://).*", message = "Deliverable URL must be a valid HTTP or HTTPS URL")
    @Size(max = 500, message = "Deliverable URL cannot exceed 500 characters")
    @Schema(description = "URL pointing to the verified proof of work (GitHub PR, demo, staging URL)", example = "https://github.com/alex-dev/openbounty-security-module/pull/1")
    private String deliverableUrl;

    @Size(max = 2000, message = "Submission notes cannot exceed 2000 characters")
    @Schema(description = "Optional developer notes detailing how to test and verify the deliverable", example = "All unit and integration tests passing. Swagger endpoint tested.")
    private String notes;
}
