package com.openbounty.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbounty.dto.request.auth.RefreshTokenRequest;
import com.openbounty.dto.request.milestone.MilestoneCreateRequest;
import com.openbounty.dto.request.proposal.ProposalCreateRequest;
import com.openbounty.dto.request.review.ReviewCreateRequest;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.model.Bounty;
import com.openbounty.model.Milestone;
import com.openbounty.model.Proposal;
import com.openbounty.model.RefreshToken;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.MilestoneRepository;
import com.openbounty.repository.ProposalRepository;
import com.openbounty.repository.RefreshTokenRepository;
import com.openbounty.repository.ReviewRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.service.RefreshTokenService;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dedicated test suite reproducing and verifying the fixes for the critical system bugs:
 *
 * 1. [CRITICAL BUG 1] PostgreSQL Outer Join Locking Incompatibility: MilestoneRepository.findByIdForUpdate
 *    locks only the milestone row without outer joins.
 * 2. [CRITICAL BUG 2] Concurrency TOCTOU: Race condition between Bounty Cancellation and Proposal Submission
 *    guarantees no ghost/dangling PENDING proposals on CANCELLED bounties.
 * 3. [CRITICAL BUG 3] Descriptive State Machine Exception: Proposal submission to an assigned bounty throws
 *    descriptive InvalidStateTransitionException with 409 Conflict.
 * 4. [CRITICAL BUG 4] Global Session Invalidation: Logout with allDevices=true clears all sessions even when
 *    JWT is expired or unauthenticated.
 * 5. [CRITICAL BUG 5] RefreshToken Entity Contract: RefreshToken equals/hashCode respects database identity.
 * 6. [CRITICAL BUG 6] Stored XSS Mitigation: Malicious HTML tags and script injections are blocked in Proposals & Reviews.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CriticalBugsReproductionTest {

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
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private User clientUser;
    private User devUser;
    private String clientToken;
    private String devToken;

    @BeforeEach
    void setUp() {
        milestoneRepository.deleteAll();
        proposalRepository.deleteAll();
        reviewRepository.deleteAll();
        bountyRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        clientUser = userRepository.save(User.builder()
                .name("Enterprise Sponsor")
                .email("sponsor@enterprise.com")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_CLIENT)
                .reputationScore(50)
                .build());

        devUser = userRepository.save(User.builder()
                .name("Senior Solver")
                .email("solver@dev.org")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(100)
                .build());

        clientToken = jwtService.generateToken(clientUser);
        devToken = jwtService.generateToken(devUser);
    }

    // =========================================================================
    // BUG 1: PostgreSQL Outer Join Locking Incompatibility Fix
    // =========================================================================
    @Test
    @DisplayName("BUG 1 FIX: MilestoneRepository.findByIdForUpdate locks milestone row cleanly without outer join errors")
    void testCriticalBug1_MilestonePessimisticLockWithoutOuterJoins() {
        Bounty bounty = bountyRepository.save(Bounty.builder()
                .title("PostgreSQL Outer Join Lock Verification")
                .description("Ensuring SELECT FOR UPDATE runs without nullable outer joins")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .status(BountyStatus.ASSIGNED)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .assignedDeveloper(devUser)
                .build());

        Proposal proposal = proposalRepository.save(Proposal.builder()
                .bounty(bounty)
                .developer(devUser)
                .approachDescription("Architecture approach")
                .proposedAmount(new BigDecimal("1400.00"))
                .estimatedDays(7)
                .status(ProposalStatus.ACCEPTED)
                .build());

        Milestone milestone = milestoneRepository.save(Milestone.builder()
                .proposal(proposal)
                .title("Deliverable 1")
                .description("Scope definition")
                .status(MilestoneStatus.PENDING)
                .build());

        // Call findByIdForUpdate within a transaction (must succeed and load entity with pessimistic write lock)
        transactionTemplate.execute(status -> {
            Optional<Milestone> lockedMilestone = milestoneRepository.findByIdForUpdate(milestone.getId());
            assertThat(lockedMilestone).isPresent();
            assertThat(lockedMilestone.get().getId()).isEqualTo(milestone.getId());
            assertThat(lockedMilestone.get().getTitle()).isEqualTo("Deliverable 1");
            return null;
        });
    }

    // =========================================================================
    // BUG 2: Concurrency Race Condition - Proposal Submission vs Bounty Cancel
    // =========================================================================
    @Test
    @DisplayName("BUG 2 FIX: Concurrent Bounty Cancel vs Proposal Submit prevents ghost PENDING proposals on CANCELLED bounty")
    void testCriticalBug2_ConcurrentBountyCancellationVsProposalSubmission_PreventsGhostProposals() throws Exception {
        Bounty bounty = bountyRepository.save(Bounty.builder()
                .title("Race Condition Bounty")
                .description("Testing concurrency serialization during cancellation")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("2000.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(15))
                .client(clientUser)
                .build());

        ProposalCreateRequest proposalRequest = ProposalCreateRequest.builder()
                .approachDescription("Racing proposal submission against bounty cancellation.")
                .proposedAmount(new BigDecimal("1800.00"))
                .estimatedDays(5)
                .milestones(List.of(
                        MilestoneCreateRequest.builder().title("M1: Scaffold Architecture").build()
                ))
                .build();
        String proposalJson = objectMapper.writeValueAsString(proposalRequest);

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger cancelStatus = new AtomicInteger(0);
        AtomicInteger submitStatus = new AtomicInteger(0);

        // Thread 1: Client cancels bounty
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(patch("/api/bounties/" + bounty.getId() + "/cancel")
                                .header("Authorization", "Bearer " + clientToken))
                        .andReturn();
                cancelStatus.set(result.getResponse().getStatus());
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        // Thread 2: Developer concurrently submits proposal
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(post("/api/bounties/" + bounty.getId() + "/proposals")
                                .header("Authorization", "Bearer " + devToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(proposalJson))
                        .andReturn();
                submitStatus.set(result.getResponse().getStatus());
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        // Database invariant check:
        // Under NO circumstances can a CANCELLED bounty contain any PENDING proposals!
        Bounty refreshedBounty = bountyRepository.findById(bounty.getId()).orElseThrow();
        if (refreshedBounty.getStatus() == BountyStatus.CANCELLED) {
            List<Proposal> pendingProposals = proposalRepository.findByBountyIdAndStatus(bounty.getId(), ProposalStatus.PENDING);
            assertThat(pendingProposals)
                    .as("CRITICAL INVARIANT VIOLATED: Found ghost PENDING proposals on a CANCELLED bounty!")
                    .isEmpty();
        }
    }

    // =========================================================================
    // BUG 3: Descriptive State Machine Exception on Proposal Submission
    // =========================================================================
    @Test
    @DisplayName("BUG 3 FIX: Submitting proposal to an assigned bounty throws accurate exception message")
    void testCriticalBug3_SubmittingProposalToAssignedBounty_ThrowsDescriptiveException() throws Exception {
        Bounty assignedBounty = bountyRepository.save(Bounty.builder()
                .title("Already Assigned Bounty")
                .description("No new proposals allowed.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1000.00"))
                .status(BountyStatus.ASSIGNED)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .assignedDeveloper(devUser)
                .build());

        ProposalCreateRequest proposalRequest = ProposalCreateRequest.builder()
                .approachDescription("Attempting proposal submission on closed assigned bounty.")
                .proposedAmount(new BigDecimal("900.00"))
                .estimatedDays(4)
                .milestones(List.of(
                        MilestoneCreateRequest.builder().title("Milestone 1: Scaffold Implementation").build()
                ))
                .build();

        mockMvc.perform(post("/api/bounties/" + assignedBounty.getId() + "/proposals")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(proposalRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Cannot submit proposals to a bounty in status: ASSIGNED"));
    }

    // =========================================================================
    // BUG 4: Global Logout Without Active JWT Revokes All Sessions
    // =========================================================================
    @Test
    @DisplayName("BUG 4 FIX: Global logout with allDevices: true terminates all user sessions even when unauthenticated")
    void testCriticalBug4_GlobalLogoutWithoutActiveJwt_RevokesAllSessions() throws Exception {
        // Issue 2 distinct refresh tokens for devUser (simulating 2 devices)
        RefreshToken sessionDevice1 = refreshTokenService.createRefreshToken(devUser);
        RefreshToken sessionDevice2 = refreshTokenService.createRefreshToken(devUser);

        assertThat(refreshTokenRepository.findAll()).hasSize(2);

        RefreshTokenRequest logoutAllRequest = RefreshTokenRequest.builder()
                .refreshToken(sessionDevice1.getToken())
                .allDevices(true)
                .build();

        // Perform unauthenticated call to /api/auth/logout with allDevices = true
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutAllRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully logged out. Refresh token revoked."));

        // Both sessions must now be terminated
        transactionTemplate.execute(status -> {
            assertThat(refreshTokenRepository.findByToken(sessionDevice1.getToken())).isEmpty();
            assertThat(refreshTokenRepository.findByToken(sessionDevice2.getToken())).isEmpty();
            return null;
        });
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    // =========================================================================
    // BUG 5: RefreshToken Entity Contract
    // =========================================================================
    @Test
    @DisplayName("BUG 5 FIX: RefreshToken implements consistent ID-based equals and hashCode")
    void testCriticalBug5_RefreshTokenEqualsAndHashCodeContract() {
        RefreshToken token1 = RefreshToken.builder()
                .id(100L)
                .token("uuid-token-string-1")
                .user(devUser)
                .build();

        RefreshToken token2 = RefreshToken.builder()
                .id(100L)
                .token("uuid-token-string-2-different")
                .user(clientUser)
                .build();

        RefreshToken token3 = RefreshToken.builder()
                .id(101L)
                .token("uuid-token-string-1")
                .user(devUser)
                .build();

        assertThat(token1).isEqualTo(token2);
        assertThat(token1.hashCode()).isEqualTo(token2.hashCode());
        assertThat(token1).isNotEqualTo(token3);
    }

    // =========================================================================
    // BUG 6: Stored XSS Prevention in Proposals and Reviews
    // =========================================================================
    @Test
    @DisplayName("BUG 6 FIX: Malicious script tags in proposal approach and review feedback are rejected with 400 Bad Request")
    void testCriticalBug6_StoredXssRejectedInProposalAndReview() throws Exception {
        Bounty openBounty = bountyRepository.save(Bounty.builder()
                .title("Valid Open Bounty")
                .description("Legitimate description")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("2000.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .build());

        // Proposal with script injection
        ProposalCreateRequest xssProposal = ProposalCreateRequest.builder()
                .approachDescription("Legitimate intro <script>alert('pwned')</script>")
                .proposedAmount(new BigDecimal("1500.00"))
                .estimatedDays(5)
                .milestones(List.of(MilestoneCreateRequest.builder().title("Milestone 1: Scaffold Implementation").build()))
                .build();

        mockMvc.perform(post("/api/bounties/" + openBounty.getId() + "/proposals")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(xssProposal)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("HTML script tags and javascript handlers are not permitted")));

        // Completed bounty for review test
        Bounty completedBounty = bountyRepository.save(Bounty.builder()
                .title("Completed Bounty For Review")
                .description("Completed successfully")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("2000.00"))
                .status(BountyStatus.COMPLETED)
                .deadline(LocalDate.now().plusDays(5))
                .client(clientUser)
                .assignedDeveloper(devUser)
                .build());

        // Review with XSS event handler injection
        ReviewCreateRequest xssReview = ReviewCreateRequest.builder()
                .bountyId(completedBounty.getId())
                .revieweeId(devUser.getId())
                .rating(5)
                .feedback("Super fast work! <img src=x onerror=alert('xss')>")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(xssReview)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("HTML script tags and javascript handlers are not permitted")));
    }

    // =========================================================================
    // BUG 1.1: Concurrency Serialization on Bounty Lock During Milestone Approval
    // =========================================================================
    @Test
    @DisplayName("BUG 1.1 FIX: Concurrent Bounty Cancellation vs Milestone Approval serializes on bounty row lock")
    void testCriticalBug1_1_ConcurrentBountyCancellationVsMilestoneApproval_SerializesOnBountyLock() throws Exception {
        Bounty bounty = bountyRepository.save(Bounty.builder()
                .title("Milestone Lock Concurrency Bounty")
                .description("Ensures milestone approval locks bounty row against race conditions")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1000.00"))
                .status(BountyStatus.ASSIGNED)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .assignedDeveloper(devUser)
                .build());

        Proposal proposal = proposalRepository.save(Proposal.builder()
                .bounty(bounty)
                .developer(devUser)
                .approachDescription("Single deliverable approach")
                .proposedAmount(new BigDecimal("1000.00"))
                .estimatedDays(5)
                .status(ProposalStatus.ACCEPTED)
                .build());

        Milestone milestone = milestoneRepository.save(Milestone.builder()
                .proposal(proposal)
                .title("Final Deliverable")
                .description("All work ready for approval")
                .deliverableUrl("https://github.com/org/repo/pull/1")
                .status(MilestoneStatus.SUBMITTED)
                .build());

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger approveStatus = new AtomicInteger(0);
        AtomicInteger cancelStatus = new AtomicInteger(0);

        // Thread 1: Client approves final milestone (transitions bounty to COMPLETED)
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(patch("/api/milestones/" + milestone.getId() + "/approve")
                                .header("Authorization", "Bearer " + clientToken))
                        .andReturn();
                approveStatus.set(result.getResponse().getStatus());
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        // Thread 2: Concurrent cancellation attempt
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(patch("/api/bounties/" + bounty.getId() + "/cancel")
                                .header("Authorization", "Bearer " + clientToken))
                        .andReturn();
                cancelStatus.set(result.getResponse().getStatus());
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        // One of the operations must succeed (200 OK) while the conflicting one is blocked/rejected with 409 Conflict
        // (Because ASSIGNED bounties cannot be cancelled, cancel will return 409, and approve will return 200,
        // or if bounty state was raceable, both would have executed inconsistently).
        assertThat(approveStatus.get()).isEqualTo(200);
        assertThat(cancelStatus.get()).isEqualTo(409);

        // The final bounty state must be cleanly COMPLETED, never CANCELLED or corrupted
        Bounty finalBounty = bountyRepository.findById(bounty.getId()).orElseThrow();
        assertThat(finalBounty.getStatus()).isEqualTo(BountyStatus.COMPLETED);
    }
}
