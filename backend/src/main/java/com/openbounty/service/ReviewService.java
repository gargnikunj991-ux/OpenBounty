package com.openbounty.service;

import com.openbounty.dto.request.review.ReviewCreateRequest;
import com.openbounty.dto.response.common.PagedResponse;
import com.openbounty.dto.response.review.ReviewResponse;
import com.openbounty.enums.BountyStatus;
import com.openbounty.exception.AccessDeniedException;
import com.openbounty.exception.BadRequestException;
import com.openbounty.exception.DuplicateResourceException;
import com.openbounty.exception.InvalidReviewException;
import com.openbounty.exception.ResourceNotFoundException;
import com.openbounty.model.Bounty;
import com.openbounty.model.Review;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.ReviewRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service managing peer reviews, ratings, and dynamic algorithmic reputation scoring.
 * Enforces mutual participation verification, completed-state requirements, and uniqueness constraints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BountyRepository bountyRepository;
    private final UserRepository userRepository;

    /**
     * Submits a peer review and star rating for a completed bounty, recalculating the recipient's reputation score.
     *
     * Guards:
     * 1. Existence: Bounty and reviewee must exist in the database.
     * 2. State Guard: Bounty must be in COMPLETED status.
     * 3. IDOR / Participation: Caller must be either the Bounty Client or the Assigned Developer.
     * 4. Mutual Reciprocity: A Client can only review the Developer; a Developer can only review the Client.
     * 5. Uniqueness: Exactly one review per reviewer-reviewee pair per bounty.
     *
     * @param request     Review submission payload containing bountyId, revieweeId, rating, and feedback
     * @param currentUser Authenticated reviewer principal
     * @return Created ReviewResponse
     */
    @Transactional
    public ReviewResponse createReview(ReviewCreateRequest request, UserPrincipal currentUser) {
        log.info("Review creation requested by user ID: {} for bounty ID: {}, reviewee ID: {}",
                currentUser.getId(), request.getBountyId(), request.getRevieweeId());

        // 1. Validate Bounty existence
        Bounty bounty = bountyRepository.findById(request.getBountyId())
                .orElseThrow(() -> new ResourceNotFoundException("Bounty", "id", request.getBountyId()));

        // 2. State Guard: Bounty must be COMPLETED
        if (bounty.getStatus() != BountyStatus.COMPLETED) {
            log.warn("Review rejected: Bounty ID {} is in status '{}' (expected COMPLETED)",
                    bounty.getId(), bounty.getStatus());
            throw new InvalidReviewException("Reviews can only be submitted for completed bounties. Current status: " + bounty.getStatus());
        }

        User client = bounty.getClient();
        User assignedDeveloper = bounty.getAssignedDeveloper();

        if (assignedDeveloper == null) {
            log.warn("Review rejected: Bounty ID {} has no assigned developer", bounty.getId());
            throw new InvalidReviewException("Cannot review a bounty with no assigned developer.");
        }

        // 3. IDOR / Participant Verification
        Long currentUserId = currentUser.getId();
        boolean isClient = currentUserId.equals(client.getId());
        boolean isDeveloper = currentUserId.equals(assignedDeveloper.getId());

        if (!isClient && !isDeveloper) {
            log.warn("IDOR attempt: User ID {} is neither client ID {} nor developer ID {} for bounty ID {}",
                    currentUserId, client.getId(), assignedDeveloper.getId(), bounty.getId());
            throw new AccessDeniedException("Only bounty participants (client or assigned developer) can submit reviews.");
        }

        // 4. Mutual Reciprocity Guard: Client reviews Dev, Dev reviews Client
        Long expectedRevieweeId = isClient ? assignedDeveloper.getId() : client.getId();
        if (!expectedRevieweeId.equals(request.getRevieweeId())) {
            log.warn("Invalid reviewee: User ID {} attempted to review user ID {} on bounty ID {} (expected reviewee ID: {})",
                    currentUserId, request.getRevieweeId(), bounty.getId(), expectedRevieweeId);
            throw new InvalidReviewException("Invalid review recipient. You can only review your counterpart on this bounty.");
        }

        // 5. Uniqueness Guard: Cannot submit duplicate reviews for the same bounty
        if (reviewRepository.existsByBountyIdAndReviewerIdAndRevieweeId(bounty.getId(), currentUserId, request.getRevieweeId())) {
            log.warn("Duplicate review attempt: Reviewer ID {} already reviewed Reviewee ID {} for bounty ID {}",
                    currentUserId, request.getRevieweeId(), bounty.getId());
            throw new DuplicateResourceException("You have already submitted a review for this bounty.");
        }

        // Fetch managed user entities
        User reviewer = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));
        User reviewee = userRepository.findById(request.getRevieweeId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", request.getRevieweeId()));

        // Build and persist review
        Review review = Review.builder()
                .bounty(bounty)
                .reviewer(reviewer)
                .reviewee(reviewee)
                .rating(request.getRating())
                .feedback(request.getFeedback())
                .build();

        Review savedReview = reviewRepository.save(review);

        // 6. Dynamic Reputation Recalculation Engine
        int delta = calculateReputationDelta(request.getRating());
        int currentScore = reviewee.getReputationScore();
        int updatedScore = Math.max(0, currentScore + delta);
        reviewee.setReputationScore(updatedScore);
        userRepository.save(reviewee);

        log.info("Review ID: {} saved successfully. Reviewee ID: {} reputation updated from {} to {} (delta: {}).",
                savedReview.getId(), reviewee.getId(), currentScore, updatedScore, delta);

        return ReviewResponse.from(savedReview);
    }

    /**
     * Retrieves paginated reviews received by a user with reviewer details eagerly fetched.
     *
     * @param userId   Target user identifier
     * @param pageable Pagination and sorting specifications
     * @return Paged review response
     */
    @Transactional(readOnly = true)
    public PagedResponse<ReviewResponse> getReviewsForUser(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "id", userId);
        }
        Page<Review> reviewPage = reviewRepository.findByRevieweeId(userId, pageable);
        return PagedResponse.from(reviewPage, ReviewResponse::from);
    }

    /**
     * Retrieves all reviews associated with a specific bounty.
     *
     * @param bountyId Target bounty identifier
     * @return List of review responses
     */
    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsForBounty(Long bountyId) {
        if (!bountyRepository.existsById(bountyId)) {
            throw new ResourceNotFoundException("Bounty", "id", bountyId);
        }
        List<Review> reviews = reviewRepository.findByBountyId(bountyId);
        return reviews.stream().map(ReviewResponse::from).toList();
    }

    /**
     * Retrieves the aggregate average star rating for a user.
     *
     * @param userId Target user identifier
     * @return Average rating double (0.0 if no reviews exist)
     */
    @Transactional(readOnly = true)
    public Double getAverageRatingForUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", "id", userId);
        }
        return reviewRepository.calculateAverageRatingForUser(userId);
    }

    /**
     * Reputation adjustment algorithm based on star rating score.
     * 5 Stars: +25 pts
     * 4 Stars: +15 pts
     * 3 Stars:  +5 pts
     * 2 Stars: -10 pts
     * 1 Star:  -25 pts
     */
    private int calculateReputationDelta(Integer rating) {
        return switch (rating) {
            case 5 -> 25;
            case 4 -> 15;
            case 3 -> 5;
            case 2 -> -10;
            case 1 -> -25;
            default -> throw new BadRequestException("Rating must be an integer between 1 and 5.");
        };
    }
}
