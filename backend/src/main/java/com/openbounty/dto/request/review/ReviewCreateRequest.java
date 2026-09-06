package com.openbounty.dto.request.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for submitting peer ratings and feedback upon bounty completion.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Review and feedback submission payload")
public class ReviewCreateRequest {

    @NotNull(message = "Bounty ID is required")
    @Schema(description = "ID of the completed bounty being reviewed", example = "101")
    private Long bountyId;

    @NotNull(message = "Reviewee user ID is required")
    @Schema(description = "ID of the user receiving the review (Client or Developer)", example = "1")
    private Long revieweeId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be an integer between 1 and 5")
    @Max(value = 5, message = "Rating must be an integer between 1 and 5")
    @Schema(description = "Numeric rating score from 1 (poor) to 5 (excellent)", example = "5")
    private Integer rating;

    @Size(max = 2000, message = "Feedback cannot exceed 2000 characters")
    @Schema(description = "Written testimonial and constructive feedback", example = "Outstanding code quality, thorough tests, and delivered 2 days ahead of schedule!")
    private String feedback;
}
