package com.openbounty.service;

import com.openbounty.dto.request.review.ReviewCreateRequest;
import com.openbounty.dto.response.common.PagedResponse;
import com.openbounty.dto.response.review.ReviewResponse;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.Role;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private BountyRepository bountyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReviewService reviewService;

    private User client;
    private User developer;
    private User outsider;
    private Bounty completedBounty;
    private Bounty openBounty;
    private UserPrincipal clientPrincipal;
    private UserPrincipal developerPrincipal;
    private UserPrincipal outsiderPrincipal;

    @BeforeEach
    void setUp() {
        client = User.builder()
                .id(1L)
                .name("Acme Client")
                .email("client@acme.com")
                .role(Role.ROLE_CLIENT)
                .reputationScore(50)
                .build();

        developer = User.builder()
                .id(2L)
                .name("Alice Developer")
                .email("alice@dev.com")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(100)
                .build();

        outsider = User.builder()
                .id(99L)
                .name("Eve Outsider")
                .email("eve@other.com")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(0)
                .build();

        completedBounty = Bounty.builder()
                .id(101L)
                .title("Complete Backend System")
                .description("Test description")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .status(BountyStatus.COMPLETED)
                .deadline(LocalDate.now().plusDays(5))
                .client(client)
                .assignedDeveloper(developer)
                .build();

        openBounty = Bounty.builder()
                .id(102L)
                .title("Incomplete Bounty")
                .description("Incomplete test")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1000.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(10))
                .client(client)
                .assignedDeveloper(null)
                .build();

        clientPrincipal = UserPrincipal.create(client);
        developerPrincipal = UserPrincipal.create(developer);
        outsiderPrincipal = UserPrincipal.create(outsider);
    }

    @Test
    @DisplayName("Should successfully create review from client to developer and award +25 reputation for 5 stars")
    void shouldCreateReviewClientToDeveloper() {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(101L)
                .revieweeId(2L)
                .rating(5)
                .feedback("Superb implementation, robust tests!")
                .build();

        when(bountyRepository.findById(101L)).thenReturn(Optional.of(completedBounty));
        when(reviewRepository.existsByBountyIdAndReviewerIdAndRevieweeId(101L, 1L, 2L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(client));
        when(userRepository.findById(2L)).thenReturn(Optional.of(developer));

        Review savedReview = Review.builder()
                .id(501L)
                .bounty(completedBounty)
                .reviewer(client)
                .reviewee(developer)
                .rating(5)
                .feedback("Superb implementation, robust tests!")
                .createdAt(LocalDateTime.now())
                .build();
        when(reviewRepository.save(any(Review.class))).thenReturn(savedReview);

        ReviewResponse response = reviewService.createReview(request, clientPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(501L);
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getFeedback()).isEqualTo("Superb implementation, robust tests!");

        // Verify developer reputation score updated from 100 to 125 (+25 for 5-star)
        assertThat(developer.getReputationScore()).isEqualTo(125);
        verify(userRepository).save(developer);
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("Should successfully create review from developer to client with 4 stars (+15 points)")
    void shouldCreateReviewDeveloperToClient() {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(101L)
                .revieweeId(1L)
                .rating(4)
                .feedback("Clear requirements and quick communication")
                .build();

        when(bountyRepository.findById(101L)).thenReturn(Optional.of(completedBounty));
        when(reviewRepository.existsByBountyIdAndReviewerIdAndRevieweeId(101L, 2L, 1L)).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(developer));
        when(userRepository.findById(1L)).thenReturn(Optional.of(client));

        Review savedReview = Review.builder()
                .id(502L)
                .bounty(completedBounty)
                .reviewer(developer)
                .reviewee(client)
                .rating(4)
                .feedback("Clear requirements and quick communication")
                .createdAt(LocalDateTime.now())
                .build();
        when(reviewRepository.save(any(Review.class))).thenReturn(savedReview);

        ReviewResponse response = reviewService.createReview(request, developerPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(502L);
        assertThat(response.getRating()).isEqualTo(4);

        // Client reputation updated from 50 to 65 (+15 for 4-star)
        assertThat(client.getReputationScore()).isEqualTo(65);
        verify(userRepository).save(client);
    }

    @Test
    @DisplayName("Should penalize reputation on 1-star review and enforce 0 floor guard")
    void shouldPenalizeReputationOnLowRatingWithFloor() {
        developer.setReputationScore(10); // only 10 points

        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(101L)
                .revieweeId(2L)
                .rating(1) // -25 penalty
                .feedback("Unsatisfactory delivery")
                .build();

        when(bountyRepository.findById(101L)).thenReturn(Optional.of(completedBounty));
        when(reviewRepository.existsByBountyIdAndReviewerIdAndRevieweeId(101L, 1L, 2L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(client));
        when(userRepository.findById(2L)).thenReturn(Optional.of(developer));

        Review savedReview = Review.builder()
                .id(503L)
                .bounty(completedBounty)
                .reviewer(client)
                .reviewee(developer)
                .rating(1)
                .feedback("Unsatisfactory delivery")
                .build();
        when(reviewRepository.save(any(Review.class))).thenReturn(savedReview);

        reviewService.createReview(request, clientPrincipal);

        // 10 - 25 = -15, but floor guard ensures score does not drop below 0
        assertThat(developer.getReputationScore()).isEqualTo(0);
        verify(userRepository).save(developer);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when bounty does not exist")
    void shouldThrowWhenBountyNotFound() {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(999L)
                .revieweeId(2L)
                .rating(5)
                .build();

        when(bountyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(request, clientPrincipal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Bounty not found with id: '999'");
    }

    @Test
    @DisplayName("Should throw InvalidReviewException when bounty is not COMPLETED")
    void shouldThrowWhenBountyNotCompleted() {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(102L)
                .revieweeId(2L)
                .rating(5)
                .build();

        when(bountyRepository.findById(102L)).thenReturn(Optional.of(openBounty));

        assertThatThrownBy(() -> reviewService.createReview(request, clientPrincipal))
                .isInstanceOf(InvalidReviewException.class)
                .hasMessageContaining("Reviews can only be submitted for completed bounties");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when outsider tries to review (anti-IDOR)")
    void shouldThrowWhenCallerIsNotParticipant() {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(101L)
                .revieweeId(2L)
                .rating(5)
                .build();

        when(bountyRepository.findById(101L)).thenReturn(Optional.of(completedBounty));

        assertThatThrownBy(() -> reviewService.createReview(request, outsiderPrincipal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only bounty participants (client or assigned developer) can submit reviews");
    }

    @Test
    @DisplayName("Should throw InvalidReviewException when client attempts to review non-assigned developer")
    void shouldThrowWhenRevieweeIsInvalid() {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(101L)
                .revieweeId(99L) // outsider, not assigned dev
                .rating(5)
                .build();

        when(bountyRepository.findById(101L)).thenReturn(Optional.of(completedBounty));

        assertThatThrownBy(() -> reviewService.createReview(request, clientPrincipal))
                .isInstanceOf(InvalidReviewException.class)
                .hasMessageContaining("Invalid review recipient");
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException on duplicate review attempt for same bounty")
    void shouldThrowOnDuplicateReview() {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(101L)
                .revieweeId(2L)
                .rating(5)
                .build();

        when(bountyRepository.findById(101L)).thenReturn(Optional.of(completedBounty));
        when(reviewRepository.existsByBountyIdAndReviewerIdAndRevieweeId(101L, 1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(request, clientPrincipal))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("You have already submitted a review for this bounty");
    }

    @Test
    @DisplayName("Should retrieve paginated reviews for a user")
    void shouldGetReviewsForUser() {
        Pageable pageable = PageRequest.of(0, 10);
        Review review = Review.builder()
                .id(1L)
                .bounty(completedBounty)
                .reviewer(client)
                .reviewee(developer)
                .rating(5)
                .feedback("Great work")
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.existsById(2L)).thenReturn(true);
        when(reviewRepository.findByRevieweeId(2L, pageable)).thenReturn(new PageImpl<>(List.of(review), pageable, 1));

        PagedResponse<ReviewResponse> response = reviewService.getReviewsForUser(2L, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getRating()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should retrieve reviews for a specific bounty")
    void shouldGetReviewsForBounty() {
        Review review = Review.builder()
                .id(1L)
                .bounty(completedBounty)
                .reviewer(client)
                .reviewee(developer)
                .rating(5)
                .feedback("Great work")
                .createdAt(LocalDateTime.now())
                .build();

        when(bountyRepository.existsById(101L)).thenReturn(true);
        when(reviewRepository.findByBountyId(101L)).thenReturn(List.of(review));

        List<ReviewResponse> responses = reviewService.getReviewsForBounty(101L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getBountyId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("Should return aggregate average rating for user")
    void shouldGetAverageRatingForUser() {
        when(userRepository.existsById(2L)).thenReturn(true);
        when(reviewRepository.calculateAverageRatingForUser(2L)).thenReturn(4.8);

        Double average = reviewService.getAverageRatingForUser(2L);

        assertThat(average).isEqualTo(4.8);
    }
}
