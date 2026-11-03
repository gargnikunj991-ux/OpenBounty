package com.openbounty.security;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adversarial & Hacker Penetration Test Suite for Phase 9 (Reviews & Reputation Engine).
 * 
 * Simulates real-world malicious attacker tactics, exploit attempts, and concurrency stress:
 * 
 * 1. [IDOR / REPUTATION SABOTAGE] Hacker attempts to leave 1-star reviews on rival developers
 *    for bounties the hacker was never a participant of.
 * 2. [SYBIL / REPUTATION FARMING] Hacker attempts to self-review or review dummy accounts to
 *    farm unearned reputation points.
 * 3. [CONCURRENCY RACE] Multi-threaded review spamming: Hacker launches 10 concurrent requests
 *    simultaneously to bypass duplicate checks and multiply reputation gains.
 * 4. [UNDERFLOW SAFEGUARD] Malicious 1-star penalty against low-reputation users cannot drive
 *    reputation below zero.
 * 5. [MASS ASSIGNMENT & FORGERY] Hacker tampers with payload parameters (e.g. injected reviewerId,
 *    reputationScore, review ID) to hijack identity.
 * 6. [FORGED JWT AUTH] Attacker crafts tokens with invalid HMAC signatures or garbage keys.
 * 7. [PREMATURE REVIEW] Attacker attempts to post reviews on ongoing, open, or cancelled bounties.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ReviewAndReputationAdversarialTest {

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

    private User legitimateClient;
    private User legitimateDeveloper;
    private User maliciousHacker;

    private String legitimateClientToken;
    private String legitimateDeveloperToken;
    private String hackerToken;

    private Bounty completedBounty;
    private Bounty openBounty;
    private Bounty cancelledBounty;

    @BeforeEach
    void setUp() {
        milestoneRepository.deleteAll();
        proposalRepository.deleteAll();
        reviewRepository.deleteAll();
        bountyRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Setup Legitimate Client
        legitimateClient = userRepository.save(User.builder()
                .name("Acme Enterprise")
                .email("sponsor@acme.com")
                .password(passwordEncoder.encode("SecurePass123!"))
                .role(Role.ROLE_CLIENT)
                .reputationScore(50)
                .build());

        // 2. Setup Legitimate Developer
        legitimateDeveloper = userRepository.save(User.builder()
                .name("Victim Pro Developer")
                .email("pro@developer.io")
                .password(passwordEncoder.encode("SecurePass123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(100)
                .build());

        // 3. Setup Malicious Hacker / Attacker
        maliciousHacker = userRepository.save(User.builder()
                .name("Evil Hacker")
                .email("hacker@darkweb.org")
                .password(passwordEncoder.encode("EvilPass123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(0)
                .build());

        legitimateClientToken = jwtService.generateToken(legitimateClient);
        legitimateDeveloperToken = jwtService.generateToken(legitimateDeveloper);
        hackerToken = jwtService.generateToken(maliciousHacker);

        // 4. Setup completed bounty between legitimateClient and legitimateDeveloper
        completedBounty = bountyRepository.save(Bounty.builder()
                .title("Enterprise High-Frequency Trading Core")
                .description("Build low-latency matching engine")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("5000.00"))
                .status(BountyStatus.COMPLETED)
                .deadline(LocalDate.now().plusDays(15))
                .client(legitimateClient)
                .assignedDeveloper(legitimateDeveloper)
                .build());

        // 5. Setup open bounty (in progress / bidding phase)
        openBounty = bountyRepository.save(Bounty.builder()
                .title("Unfinished Active Challenge")
                .description("Still accepting proposals")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1000.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(10))
                .client(legitimateClient)
                .assignedDeveloper(null)
                .build());

        // 6. Setup cancelled bounty
        cancelledBounty = bountyRepository.save(Bounty.builder()
                .title("Abandoned Project")
                .description("Cancelled by sponsor")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("800.00"))
                .status(BountyStatus.CANCELLED)
                .deadline(LocalDate.now().plusDays(5))
                .client(legitimateClient)
                .assignedDeveloper(null)
                .build());
    }

    @Test
    @DisplayName("HACKER ATTACK 1: IDOR & Smear Campaign — Malicious hacker attempts to leave 1-star review on victim's completed bounty (Expects 403 Forbidden)")
    void hackerAttemptsCrossTenantIDORSMRating_Blocked() throws Exception {
        ReviewCreateRequest maliciousRequest = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(legitimateDeveloper.getId())
                .rating(1) // malicious 1-star rating aimed at sabotaging reputation
                .feedback("Terrible coder, wrote malware into the codebase! Do not hire!")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + hackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.detail", containsString("Only bounty participants (client or assigned developer) can submit reviews")));

        // VERIFY: Victim's reputation score is completely untouched (still 100)
        User victimAfterAttack = userRepository.findById(legitimateDeveloper.getId()).orElseThrow();
        assertThat(victimAfterAttack.getReputationScore()).isEqualTo(100);

        // VERIFY: Zero fake reviews were written to database
        assertThat(reviewRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("HACKER ATTACK 2: Sybil Self-Review & Reputation Farming — Hacker attempts to submit a review targeting themselves (Expects 400 Bad Request)")
    void hackerAttemptsSelfReviewFarming_Blocked() throws Exception {
        ReviewCreateRequest selfReviewRequest = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(legitimateClient.getId()) // Hacker is neither client nor developer
                .rating(5)
                .feedback("Farming reputation points")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + hackerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfReviewRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));

        // If client tries to review themselves on their own bounty:
        ReviewCreateRequest clientSelfReview = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(legitimateClient.getId()) // Client reviewing client
                .rating(5)
                .feedback("I am awesome!")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + legitimateClientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(clientSelfReview)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Review"))
                .andExpect(jsonPath("$.detail", containsString("Invalid review recipient")));

        assertThat(reviewRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("HACKER ATTACK 3: High-Concurrency Review Spam Race Condition — 10 concurrent threads spamming review submission simultaneously (Expects Exactly 1 Success and 9 Conflicts)")
    void concurrentReviewSpamRaceCondition_StrictlyEnforced() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger conflictCounter = new AtomicInteger(0);

        ReviewCreateRequest reviewPayload = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(legitimateDeveloper.getId())
                .rating(5)
                .feedback("Fastest matching engine built in Java 21!")
                .build();

        String payloadJson = objectMapper.writeValueAsString(reviewPayload);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Simultaneous unleash across all 10 threads
                    MvcResult result = mockMvc.perform(post("/api/reviews")
                                    .header("Authorization", "Bearer " + legitimateClientToken)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payloadJson))
                            .andReturn();

                    int statusCode = result.getResponse().getStatus();
                    statusCodes.add(statusCode);

                    if (statusCode == 201) {
                        successCounter.incrementAndGet();
                    } else if (statusCode == 409) {
                        conflictCounter.incrementAndGet();
                    }
                } catch (Exception e) {
                    // unexpected error
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // FIRE!
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // VERIFY: Exactly 1 submission succeeded (201 Created)
        assertThat(successCounter.get()).isEqualTo(1);

        // VERIFY: The remaining 9 concurrent requests were blocked by duplicate/unique constraint guards (409 Conflict)
        assertThat(conflictCounter.get()).isEqualTo(threadCount - 1);

        // VERIFY: Exactly 1 review record persisted in database
        assertThat(reviewRepository.count()).isEqualTo(1);

        // VERIFY: Developer reputation increased by EXACTLY +25 (from 100 to 125, NOT 100 + 250)
        User developerAfterSpam = userRepository.findById(legitimateDeveloper.getId()).orElseThrow();
        assertThat(developerAfterSpam.getReputationScore()).isEqualTo(125);
    }

    @Test
    @DisplayName("HACKER ATTACK 4: Reputation Underflow Attack — 1-star penalty against zero-reputation user does not cause negative integer corruption")
    void reputationScoreNeverDropsBelowZero_FloorGuarded() throws Exception {
        // Set developer reputation to 5 points
        legitimateDeveloper.setReputationScore(5);
        userRepository.save(legitimateDeveloper);

        ReviewCreateRequest harshReview = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(legitimateDeveloper.getId())
                .rating(1) // -25 reputation penalty
                .feedback("Unsatisfactory code quality")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + legitimateClientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(harshReview)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(1));

        // 5 - 25 = -20 mathematically, but system enforces Math.max(0, updatedScore)
        User developerAfterPenalty = userRepository.findById(legitimateDeveloper.getId()).orElseThrow();
        assertThat(developerAfterPenalty.getReputationScore()).isEqualTo(0);
        assertThat(developerAfterPenalty.getReputationScore()).isNotNegative();
    }

    @Test
    @DisplayName("HACKER ATTACK 5: Parameter Tampering & Mass-Assignment — Injected reviewerId and forged reputation fields are ignored")
    void massAssignmentParameterTampering_Ignored() throws Exception {
        // Hacker tries to impersonate another user via injected JSON fields
        String forgedPayload = """
                {
                    "bountyId": %d,
                    "revieweeId": %d,
                    "rating": 5,
                    "reviewerId": 99999,
                    "id": 88888,
                    "reputationScore": 1000000,
                    "feedback": "Attempting parameter tampering"
                }
                """.formatted(completedBounty.getId(), legitimateDeveloper.getId());

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + legitimateClientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgedPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewerId").value(legitimateClient.getId())) // Identity bound to JWT, NOT payload!
                .andExpect(jsonPath("$.id").isNumber());

        // Verify database row reflects true authenticated user ID
        List<Review> reviews = reviewRepository.findByBountyId(completedBounty.getId());
        assertThat(reviews).hasSize(1);
        assertThat(reviews.get(0).getReviewer().getId()).isEqualTo(legitimateClient.getId());
    }

    @Test
    @DisplayName("HACKER ATTACK 6: Cryptographic JWT Forgery — Fake HMAC-SHA256 signature returns 401 Unauthorized before reaching business logic")
    void forgedHmacSignature_RejectedAtGatewayFilter() throws Exception {
        String forgedJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
                "eyJzdWIiOiJoYWNrZXJAZGFya3dlYi5vcmciLCJyb2xlcyI6WyJST0xFX0RFVkVMT1BFUiJdfQ." +
                "InVaLiD_SiGnAtUrE_FoRgEd_By_HaCkEr_99999999999";

        ReviewCreateRequest request = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(legitimateDeveloper.getId())
                .rating(5)
                .feedback("Forged token review")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + forgedJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        assertThat(reviewRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("HACKER ATTACK 7: Premature Reviews — Attacker attempts to post review on OPEN and CANCELLED bounties (Expects 400 Bad Request)")
    void prematureReviewsOnUncompletedBounties_Rejected() throws Exception {
        // Attempt on OPEN bounty
        ReviewCreateRequest openBountyReview = ReviewCreateRequest.builder()
                .bountyId(openBounty.getId())
                .revieweeId(legitimateDeveloper.getId())
                .rating(5)
                .feedback("Premature review on open bounty")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + legitimateClientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(openBountyReview)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Review"))
                .andExpect(jsonPath("$.detail", containsString("Reviews can only be submitted for completed bounties")));

        // Attempt on CANCELLED bounty
        ReviewCreateRequest cancelledBountyReview = ReviewCreateRequest.builder()
                .bountyId(cancelledBounty.getId())
                .revieweeId(legitimateDeveloper.getId())
                .rating(1)
                .feedback("Reviewing a dead cancelled bounty")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + legitimateClientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelledBountyReview)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Review"));

        assertThat(reviewRepository.count()).isEqualTo(0);
    }
}
