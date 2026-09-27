package com.openbounty.controller;

import com.openbounty.dto.request.review.ReviewCreateRequest;
import com.openbounty.dto.response.ErrorResponse;
import com.openbounty.dto.response.common.PagedResponse;
import com.openbounty.dto.response.review.ReviewResponse;
import com.openbounty.security.UserPrincipal;
import com.openbounty.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller managing peer ratings, testimonials, and solver reputation feedback.
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews & Ratings", description = "Peer review submission, reputation scoring, and star rating history")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'DEVELOPER', 'ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Submit peer review and rating",
            description = "Allows a client or developer of a COMPLETED bounty to rate and review their counterpart. Recalculates recipient reputation score.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Review submitted successfully",
                    content = @Content(schema = @Schema(implementation = ReviewResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid review (bounty not completed, wrong reviewee, invalid rating)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Caller is not a participant in the bounty",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Bounty or user not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflict - Duplicate review submission for this bounty",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ReviewResponse> createReview(
            @Valid @RequestBody ReviewCreateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        ReviewResponse response = reviewService.createReview(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get reviews received by user", description = "Returns a paginated list of reviews and ratings received by a specific user.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paginated reviews retrieved",
                    content = @Content(schema = @Schema(implementation = PagedResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PagedResponse<ReviewResponse>> getReviewsForUser(
            @Parameter(description = "ID of user who received the reviews") @PathVariable Long userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(reviewService.getReviewsForUser(userId, pageable));
    }

    @GetMapping("/bounty/{bountyId}")
    @Operation(summary = "Get reviews for a bounty", description = "Returns all mutual peer reviews submitted for a specific completed bounty.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bounty reviews retrieved",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = ReviewResponse.class)))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Bounty not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<List<ReviewResponse>> getReviewsForBounty(
            @Parameter(description = "ID of the completed bounty") @PathVariable Long bountyId
    ) {
        return ResponseEntity.ok(reviewService.getReviewsForBounty(bountyId));
    }

    @GetMapping("/user/{userId}/rating")
    @Operation(summary = "Get average star rating for user", description = "Returns aggregate average rating score for a user across all received reviews.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Average rating calculated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Map<String, Object>> getAverageRatingForUser(
            @Parameter(description = "ID of user to calculate rating for") @PathVariable Long userId
    ) {
        Double averageRating = reviewService.getAverageRatingForUser(userId);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "averageRating", averageRating
        ));
    }
}
