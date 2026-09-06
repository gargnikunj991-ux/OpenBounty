package com.openbounty.dto.response.review;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.openbounty.dto.response.auth.UserSummaryResponse;
import com.openbounty.model.Review;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Detailed representation of a completed review/rating transaction.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User review and reputation feedback response")
public class ReviewResponse {

    @Schema(description = "Review unique identifier", example = "401")
    private Long id;

    @Schema(description = "Associated completed bounty ID", example = "101")
    private Long bountyId;

    @Schema(description = "ID of reviewer who authored the review", example = "5")
    private Long reviewerId;

    @Schema(description = "ID of reviewee receiving the rating", example = "1")
    private Long revieweeId;

    @Schema(description = "Review author user profile summary")
    private UserSummaryResponse reviewer;

    @Schema(description = "Review recipient user profile summary")
    private UserSummaryResponse reviewee;

    @Schema(description = "Star rating score (1 to 5)", example = "5")
    private Integer rating;

    @Schema(description = "Review written feedback / testimonial", example = "Outstanding code quality, thorough tests, and delivered 2 days ahead of schedule!")
    private String feedback;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @Schema(description = "Review creation timestamp", example = "2026-08-31T21:30:00.000Z")
    private LocalDateTime createdAt;

    public static ReviewResponse from(Review review) {
        if (review == null) {
            return null;
        }
        return ReviewResponse.builder()
                .id(review.getId())
                .bountyId(review.getBounty() != null ? review.getBounty().getId() : null)
                .reviewerId(review.getReviewer() != null ? review.getReviewer().getId() : null)
                .revieweeId(review.getReviewee() != null ? review.getReviewee().getId() : null)
                .reviewer(UserSummaryResponse.from(review.getReviewer()))
                .reviewee(UserSummaryResponse.from(review.getReviewee()))
                .rating(review.getRating())
                .feedback(review.getFeedback())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
