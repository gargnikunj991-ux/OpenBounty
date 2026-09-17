package com.openbounty.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbounty.dto.request.auth.RefreshTokenRequest;
import com.openbounty.dto.request.bounty.BountyCreateRequest;
import com.openbounty.dto.request.milestone.MilestoneCreateRequest;
import com.openbounty.dto.request.proposal.ProposalCreateRequest;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.model.Bounty;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * System-Wide Adversarial & Resilience Test Suite.
 * 
 * Simulates real-world attacker techniques, chaos conditions, and user misbehaviors
 * spanning multiple modules across the entire OpenBounty platform:
 * 
 * 1. [AUTH] Refresh Token Race Condition (TOCTOU Session Cloning / Replay Bypass)
 * 2. [BOUNTY + PROPOSAL] Ghost Proposals on Bounty Cancellation (Dangling / Inconsistent State)
 * 3. [SEARCH / ORM] Property Reference Injection / DoS via Invalid Sort Field
 * 4. [CONCURRENCY] TOCTOU Race Condition: Bounty Cancellation vs. Proposal Acceptance
 * 5. [DATA INTEGRITY] Storing and Reflecting Raw XSS / Script Payloads in Bounty Definitions
 */
@SpringBootTest
@AutoConfigureMockMvc
public class SystemWideAdversarialTest {

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
                .name("Acme Enterprise")
                .email("client@acme.com")
                .password(passwordEncoder.encode("SecurePass123!"))
                .role(Role.ROLE_CLIENT)
                .reputationScore(50)
                .build());

        devUser = userRepository.save(User.builder()
                .name("Elite Hacker Dev")
                .email("hacker@dev.org")
                .password(passwordEncoder.encode("SecurePass123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(100)
                .build());

        clientToken = jwtService.generateToken(clientUser);
        devToken = jwtService.generateToken(devUser);
    }

    // =========================================================================
    // ATTACK 1: Refresh Token Race Condition (Session Cloning Exploit)
    // =========================================================================
    @Test
    @DisplayName("ATTACK 1: Concurrent refresh with identical token MUST NOT grant multiple sessions (RTR Violation)")
    void attack_concurrentRefreshTokenRotation_ShouldNotAllowSessionCloning() throws Exception {
        // Issue an initial valid refresh token for the developer
        RefreshToken initialToken = refreshTokenService.createRefreshToken(devUser);

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        RefreshTokenRequest request = new RefreshTokenRequest(initialToken.getToken(), false);
        String payload = objectMapper.writeValueAsString(request);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();

        // SECURITY REQUIREMENT:
        // Refresh Token Rotation (RTR) guarantees a single-use token lifecycle.
        // If an attacker and a legitimate client race simultaneously with the same token,
        // exactly ONE request may succeed, or BOTH should fail with token reuse detection.
        // Under no circumstances should BOTH requests succeed and create 2 distinct cloned sessions!
        assertThat(successCount.get())
                .as("Security vulnerability: Both concurrent refresh requests succeeded! Session was cloned.")
                .isEqualTo(1);

        assertThat(failureCount.get())
                .as("At least one request must fail due to token reuse / invalidation")
                .isEqualTo(1);
    }

    // =========================================================================
    // ATTACK 2: Ghost Proposals on Bounty Cancellation (Dangling State Invariant)
    // =========================================================================
    @Test
    @DisplayName("ATTACK 2: Cancelling a bounty MUST NOT leave submitted proposals as PENDING (Ghost Proposals)")
    void attack_cancellingBounty_MustCleanUpOrRejectDanglingProposals() throws Exception {
        // Create an open bounty
        Bounty bounty = bountyRepository.save(Bounty.builder()
                .title("High Value Security Architecture")
                .description("Design our zero-trust infrastructure.")
                .category(BountyCategory.SECURITY_AUDIT)
                .rewardAmount(new BigDecimal("5000.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(20))
                .client(clientUser)
                .build());

        // Developer submits a legitimate proposal
        Proposal proposal = proposalRepository.save(Proposal.builder()
                .bounty(bounty)
                .developer(devUser)
                .approachDescription("Comprehensive zero trust network segmentation approach.")
                .proposedAmount(new BigDecimal("4500.00"))
                .estimatedDays(14)
                .status(ProposalStatus.PENDING)
                .build());

        // Client cancels the bounty
        mockMvc.perform(patch("/api/bounties/" + bounty.getId() + "/cancel")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // STATE INVARIANT REQUIREMENT:
        // When a bounty is CANCELLED, any developer who submitted a proposal cannot be left in PENDING limbo!
        // The proposals must be automatically transitioned to REJECTED or CANCELLED so developers are aware.
        List<Proposal> danglingPendingProposals = proposalRepository.findByBountyIdAndStatus(bounty.getId(), ProposalStatus.PENDING);
        assertThat(danglingPendingProposals)
                .as("Integrity flaw: Submitted proposals are still PENDING on a CANCELLED bounty!")
                .isEmpty();
    }

    // =========================================================================
    // ATTACK 3: Property Reference Injection / DoS via Invalid Sort Field
    // =========================================================================
    @Test
    @DisplayName("ATTACK 3: Invalid or sensitive sort parameters MUST return 400 Bad Request, NOT unhandled 500")
    void attack_invalidSortProperty_ShouldReturn400BadRequestNot500InternalError() throws Exception {
        // Attacker attempts to probe internal properties or trigger unchecked PropertyReferenceException
        mockMvc.perform(get("/api/bounties")
                        .param("sort", "client.password,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://api.openbounty.dev/errors/bad-request"));
    }

    // =========================================================================
    // ATTACK 4: Concurrency TOCTOU - Bounty Cancellation vs Proposal Acceptance
    // =========================================================================
    @Test
    @DisplayName("ATTACK 4: Concurrent Cancel vs Accept MUST NOT produce a CANCELLED bounty with an ASSIGNED developer")
    void attack_concurrentCancelAndAccept_MustMaintainStateConsistency() throws Exception {
        Bounty bounty = bountyRepository.save(Bounty.builder()
                .title("Cloud Infrastructure Migration")
                .description("Migrate legacy cluster to Kubernetes.")
                .category(BountyCategory.DEVOPS_CLOUD)
                .rewardAmount(new BigDecimal("3000.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(15))
                .client(clientUser)
                .build());

        Proposal proposal = proposalRepository.save(Proposal.builder()
                .bounty(bounty)
                .developer(devUser)
                .approachDescription("Automated Terraform and Helm rollout.")
                .proposedAmount(new BigDecimal("2800.00"))
                .estimatedDays(10)
                .status(ProposalStatus.PENDING)
                .build());

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        // Thread 1: Cancel Bounty
        executor.submit(() -> {
            try {
                startLatch.await();
                mockMvc.perform(patch("/api/bounties/" + bounty.getId() + "/cancel")
                        .header("Authorization", "Bearer " + clientToken));
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        // Thread 2: Accept Proposal
        executor.submit(() -> {
            try {
                startLatch.await();
                mockMvc.perform(patch("/api/proposals/" + proposal.getId() + "/accept")
                        .header("Authorization", "Bearer " + clientToken));
            } catch (Exception ignored) {
            } finally {
                finishLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean finished = finishLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();

        // Check the resulting database state
        Bounty refreshedBounty = bountyRepository.findById(bounty.getId()).orElseThrow();
        Proposal refreshedProposal = proposalRepository.findById(proposal.getId()).orElseThrow();

        // CRITICAL INVARIANT:
        // A CANCELLED bounty can NEVER have an assigned developer or an ACCEPTED proposal!
        if (refreshedBounty.getStatus() == BountyStatus.CANCELLED) {
            assertThat(refreshedBounty.getAssignedDeveloper())
                    .as("Inconsistent state: Bounty is CANCELLED but assignedDeveloper is not null!")
                    .isNull();

            assertThat(refreshedProposal.getStatus())
                    .as("Inconsistent state: Bounty is CANCELLED but winning proposal is marked ACCEPTED!")
                    .isNotEqualTo(ProposalStatus.ACCEPTED);
        }
    }

    // =========================================================================
    // ATTACK 5: Stored XSS / Script Injection in Bounty Specification
    // =========================================================================
    @Test
    @DisplayName("ATTACK 5: Script tags in bounty title/description MUST NOT be stored raw and reflected unescaped")
    void attack_scriptTagInjection_ShouldBeSanitizedOrRejected() throws Exception {
        String xssTitle = "Reflected XSS <script>alert('pwned')</script>";
        String xssDescription = "Vulnerable challenge with <img src=x onerror=alert(document.cookie)> payload inside.";

        BountyCreateRequest xssRequest = BountyCreateRequest.builder()
                .title(xssTitle)
                .description(xssDescription)
                .category(BountyCategory.WEB_DEVELOPMENT)
                .rewardAmount(new BigDecimal("1200.00"))
                .deadline(LocalDate.now().plusDays(10))
                .build();

        // The system should either reject HTML script tags with 400 Bad Request,
        // or sanitize / escape the stored content so raw script tags do not exist in responses.
        MvcResult result = mockMvc.perform(post("/api/bounties")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(xssRequest)))
                .andReturn();

        int httpStatus = result.getResponse().getStatus();
        if (httpStatus == 201) {
            String content = result.getResponse().getContentAsString();
            // If accepted, verify that raw unescaped script tag is not stored verbatim
            assertThat(content)
                    .as("Security vulnerability: Raw unescaped <script> tag stored and reflected in API output!")
                    .doesNotContain("<script>");
        } else {
            // Alternatively, rejecting malicious payloads with 400 Bad Request is secure
            assertThat(httpStatus).isEqualTo(400);
        }
    }
}
