package com.openbounty.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbounty.dto.request.review.ReviewCreateRequest;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.Role;
import com.openbounty.model.Bounty;
import com.openbounty.model.Review;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.MilestoneRepository;
import com.openbounty.repository.ProposalRepository;
import com.openbounty.repository.RefreshTokenRepository;
import com.openbounty.repository.ReviewRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BountyRepository bountyRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User clientUser;
    private User devUser;
    private User outsiderUser;

    private String clientToken;
    private String devToken;
    private String outsiderToken;

    private Bounty completedBounty;
    private Bounty openBounty;

    @BeforeEach
    void setUp() {
        milestoneRepository.deleteAll();
        proposalRepository.deleteAll();
        reviewRepository.deleteAll();
        bountyRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        clientUser = userRepository.save(User.builder()
                .name("Acme Corp")
                .email("lead@acmecorp.io")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_CLIENT)
                .reputationScore(50)
                .build());

        devUser = userRepository.save(User.builder()
                .name("Alex Solver")
                .email("alex@solver.dev")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(100)
                .build());

        outsiderUser = userRepository.save(User.builder()
                .name("Eve Hacker")
                .email("eve@shadow.org")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(0)
                .build());

        clientToken = jwtService.generateToken(clientUser);
        devToken = jwtService.generateToken(devUser);
        outsiderToken = jwtService.generateToken(outsiderUser);

        completedBounty = bountyRepository.save(Bounty.builder()
                .title("Build Full Microservice Suite")
                .description("Production ready microservice system")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("2000.00"))
                .status(BountyStatus.COMPLETED)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .assignedDeveloper(devUser)
                .build());

        openBounty = bountyRepository.save(Bounty.builder()
                .title("Incomplete Active Bounty")
                .description("Incomplete bounty description")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1000.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .assignedDeveloper(null)
                .build());
    }

    @Test
    @DisplayName("POST /api/reviews - Client submits 5-star review for developer (201 Created & +25 reputation)")
    void clientSubmitsReviewForDeveloper_Success() throws Exception {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(devUser.getId())
                .rating(5)
                .feedback("Exceptional delivery, flawless code and test coverage!")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.bountyId").value(completedBounty.getId()))
                .andExpect(jsonPath("$.reviewerId").value(clientUser.getId()))
                .andExpect(jsonPath("$.revieweeId").value(devUser.getId()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.feedback").value("Exceptional delivery, flawless code and test coverage!"));

        // Verify developer reputation score was boosted from 100 to 125
        User updatedDev = userRepository.findById(devUser.getId()).orElseThrow();
        assertThat(updatedDev.getReputationScore()).isEqualTo(125);
    }

    @Test
    @DisplayName("POST /api/reviews - Developer submits 4-star review for client (201 Created & +15 reputation)")
    void developerSubmitsReviewForClient_Success() throws Exception {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(clientUser.getId())
                .rating(4)
                .feedback("Prompt requirements and clear communication.")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.reviewerId").value(devUser.getId()))
                .andExpect(jsonPath("$.revieweeId").value(clientUser.getId()));

        User updatedClient = userRepository.findById(clientUser.getId()).orElseThrow();
        assertThat(updatedClient.getReputationScore()).isEqualTo(65);
    }

    @Test
    @DisplayName("POST /api/reviews - Rejects review when bounty is not COMPLETED (400 Bad Request)")
    void rejectsReviewWhenBountyNotCompleted() throws Exception {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(openBounty.getId())
                .revieweeId(devUser.getId())
                .rating(5)
                .feedback("Premature review")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Review"))
                .andExpect(jsonPath("$.detail", containsString("Reviews can only be submitted for completed bounties")));
    }

    @Test
    @DisplayName("POST /api/reviews - Rejects review when caller is non-participant outsider (403 Forbidden)")
    void rejectsReviewWhenCallerIsOutsider() throws Exception {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(devUser.getId())
                .rating(5)
                .feedback("Outsider attempt")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));
    }

    @Test
    @DisplayName("POST /api/reviews - Rejects review when reviewee is not the counterpart (400 Bad Request)")
    void rejectsReviewWhenRevieweeIsInvalid() throws Exception {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(outsiderUser.getId())
                .rating(5)
                .feedback("Wrong target")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Review"))
                .andExpect(jsonPath("$.detail", containsString("Invalid review recipient")));
    }

    @Test
    @DisplayName("POST /api/reviews - Rejects duplicate review submission for the same bounty (409 Conflict)")
    void rejectsDuplicateReviewSubmission() throws Exception {
        // First submission succeeds
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(devUser.getId())
                .rating(5)
                .feedback("First review")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second submission must return 409 Conflict
        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Resource Conflict"))
                .andExpect(jsonPath("$.detail", containsString("already submitted a review")));
    }

    @Test
    @DisplayName("POST /api/reviews - Unauthenticated request returns 401 Unauthorized")
    void rejectsUnauthenticatedRequest() throws Exception {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(devUser.getId())
                .rating(5)
                .build();

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/reviews - Validation failure for out-of-range rating (400 Bad Request)")
    void rejectsOutOfRangeRating() throws Exception {
        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(devUser.getId())
                .rating(6) // Max is 5
                .feedback("Out of range")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("GET /api/reviews/user/{userId} - Public paginated review list (200 OK)")
    void getsUserReviews_PublicAccess() throws Exception {
        reviewRepository.save(Review.builder()
                .bounty(completedBounty)
                .reviewer(clientUser)
                .reviewee(devUser)
                .rating(5)
                .feedback("Rockstar engineer")
                .build());

        mockMvc.perform(get("/api/reviews/user/" + devUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.content[0].feedback").value("Rockstar engineer"));
    }

    @Test
    @DisplayName("GET /api/reviews/bounty/{bountyId} - Public bounty review list (200 OK)")
    void getsBountyReviews_PublicAccess() throws Exception {
        reviewRepository.save(Review.builder()
                .bounty(completedBounty)
                .reviewer(clientUser)
                .reviewee(devUser)
                .rating(5)
                .feedback("Bounty review test")
                .build());

        mockMvc.perform(get("/api/reviews/bounty/" + completedBounty.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].bountyId").value(completedBounty.getId()));
    }

    @Test
    @DisplayName("GET /api/reviews/user/{userId}/rating - Public average rating summary (200 OK)")
    void getsUserAverageRating_PublicAccess() throws Exception {
        reviewRepository.save(Review.builder()
                .bounty(completedBounty)
                .reviewer(clientUser)
                .reviewee(devUser)
                .rating(5)
                .feedback("Review 1")
                .build());

        mockMvc.perform(get("/api/reviews/user/" + devUser.getId() + "/rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(devUser.getId()))
                .andExpect(jsonPath("$.averageRating").value(5.0));
    }
}
