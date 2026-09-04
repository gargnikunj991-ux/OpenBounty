package com.openbounty.repository;

import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.Role;
import com.openbounty.model.Bounty;
import com.openbounty.model.Review;
import com.openbounty.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ReviewRepositoryTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User client;
    private User dev;
    private Bounty bounty;
    private Review review1;

    @BeforeEach
    void setUp() {
        client = User.builder()
                .name("Delta Org")
                .email("delta@org.com")
                .password("hash")
                .role(Role.ROLE_CLIENT)
                .build();
        entityManager.persist(client);

        dev = User.builder()
                .name("Dev Dave")
                .email("dave@openbounty.dev")
                .password("hash")
                .role(Role.ROLE_DEVELOPER)
                .build();
        entityManager.persist(dev);

        bounty = Bounty.builder()
                .title("PostgreSQL Query Optimizer")
                .description("Tune slow queries and add composite indexes")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1200.00"))
                .status(BountyStatus.COMPLETED)
                .deadline(LocalDate.now().minusDays(5))
                .client(client)
                .assignedDeveloper(dev)
                .build();
        entityManager.persist(bounty);

        review1 = Review.builder()
                .bounty(bounty)
                .reviewer(client)
                .reviewee(dev)
                .rating(5)
                .feedback("Superb work! Queries are 10x faster.")
                .build();
        entityManager.persist(review1);

        entityManager.flush();
    }

    @Test
    @DisplayName("existsByBountyIdAndReviewerIdAndRevieweeId checks if review already exists")
    void testExistsByBountyIdAndReviewerIdAndRevieweeId() {
        boolean exists = reviewRepository.existsByBountyIdAndReviewerIdAndRevieweeId(
                bounty.getId(), client.getId(), dev.getId());
        assertThat(exists).isTrue();

        boolean reverseExists = reviewRepository.existsByBountyIdAndReviewerIdAndRevieweeId(
                bounty.getId(), dev.getId(), client.getId());
        assertThat(reverseExists).isFalse();
    }

    @Test
    @DisplayName("existsByBountyIdAndReviewerId checks if reviewer gave review for bounty")
    void testExistsByBountyIdAndReviewerId() {
        assertThat(reviewRepository.existsByBountyIdAndReviewerId(bounty.getId(), client.getId())).isTrue();
        assertThat(reviewRepository.existsByBountyIdAndReviewerId(bounty.getId(), dev.getId())).isFalse();
    }

    @Test
    @DisplayName("findByRevieweeId returns paginated reviews for a user")
    void testFindByRevieweeId() {
        Page<Review> page = reviewRepository.findByRevieweeId(dev.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1L);
        assertThat(page.getContent().get(0).getRating()).isEqualTo(5);
        assertThat(page.getContent().get(0).getReviewer().getName()).isEqualTo("Delta Org");
    }

    @Test
    @DisplayName("findByBountyId returns all reviews for a bounty")
    void testFindByBountyId() {
        List<Review> reviews = reviewRepository.findByBountyId(bounty.getId());

        assertThat(reviews).hasSize(1);
    }

    @Test
    @DisplayName("calculateAverageRatingForUser computes accurate arithmetic mean")
    void testCalculateAverageRatingForUser() {
        // Currently 1 review of rating 5
        Double avg = reviewRepository.calculateAverageRatingForUser(dev.getId());
        assertThat(avg).isEqualTo(5.0);

        // Add a second review of rating 4 for Dave from a different bounty
        Bounty bounty2 = Bounty.builder()
                .title("Second Bounty")
                .description("Second completed task")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("800.00"))
                .status(BountyStatus.COMPLETED)
                .deadline(LocalDate.now().minusDays(1))
                .client(client)
                .assignedDeveloper(dev)
                .build();
        entityManager.persist(bounty2);

        Review review2 = Review.builder()
                .bounty(bounty2)
                .reviewer(client)
                .reviewee(dev)
                .rating(4)
                .feedback("Good work, timely delivery.")
                .build();
        entityManager.persist(review2);
        entityManager.flush();

        Double newAvg = reviewRepository.calculateAverageRatingForUser(dev.getId());
        assertThat(newAvg).isEqualTo(4.5);
    }

    @Test
    @DisplayName("calculateAverageRatingForUser returns 0.0 when user has no reviews")
    void testCalculateAverageRatingForUser_NoReviews() {
        Double avg = reviewRepository.calculateAverageRatingForUser(client.getId());
        assertThat(avg).isEqualTo(0.0);
    }

    @Test
    @DisplayName("countByRevieweeId returns total number of reviews for a user")
    void testCountByRevieweeId() {
        assertThat(reviewRepository.countByRevieweeId(dev.getId())).isEqualTo(1L);
        assertThat(reviewRepository.countByRevieweeId(client.getId())).isEqualTo(0L);
    }

    @Test
    @DisplayName("findByRevieweeId returns empty page when user has no reviews")
    void testFindByRevieweeId_NoReviews() {
        Page<Review> page = reviewRepository.findByRevieweeId(client.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(0L);
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("saving duplicate review for same bounty and reviewer/reviewee violates unique constraint")
    void testSave_DuplicateReview_ThrowsException() {
        Review duplicateReview = Review.builder()
                .bounty(bounty)
                .reviewer(client)
                .reviewee(dev)
                .rating(4)
                .feedback("Another review by same client for same dev on same bounty")
                .build();

        assertThatThrownBy(() -> {
            entityManager.persist(duplicateReview);
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }
}
