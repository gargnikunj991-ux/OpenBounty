package com.openbounty.repository;

import com.openbounty.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Check if a reviewer has already reviewed a reviewee for a specific bounty.
     * Backed by unique constraint 'uq_review_participant' (bounty_id, reviewer_id, reviewee_id).
     */
    boolean existsByBountyIdAndReviewerIdAndRevieweeId(Long bountyId, Long reviewerId, Long revieweeId);

    /**
     * Check if a specific user has submitted any review for a given bounty.
     * Backed by index 'idx_reviews_bounty_id'.
     */
    boolean existsByBountyIdAndReviewerId(Long bountyId, Long reviewerId);

    /**
     * Paginated list of reviews received by a given user.
     * Eagerly fetches the reviewer profile and bounty context to display reviews without N+1 queries.
     * Backed by index 'idx_reviews_reviewee_id'.
     */
    @EntityGraph(attributePaths = {"reviewer", "bounty"})
    Page<Review> findByRevieweeId(Long revieweeId, Pageable pageable);

    /**
     * Retrieve all reviews associated with a specific bounty.
     * Eagerly fetches reviewer and reviewee profiles.
     * Backed by index 'idx_reviews_bounty_id'.
     */
    @EntityGraph(attributePaths = {"reviewer", "reviewee"})
    List<Review> findByBountyId(Long bountyId);

    /**
     * Aggregate query calculating the average review rating for a given user.
     * Returns 0.0 if the user has received no reviews.
     * Backed by index 'idx_reviews_reviewee_id'.
     */
    @Query("SELECT COALESCE(AVG(CAST(r.rating AS double)), 0.0) FROM Review r WHERE r.reviewee.id = :revieweeId")
    Double calculateAverageRatingForUser(@Param("revieweeId") Long revieweeId);

    /**
     * Total number of reviews received by a user.
     * Backed by index 'idx_reviews_reviewee_id'.
     */
    long countByRevieweeId(Long revieweeId);
}
