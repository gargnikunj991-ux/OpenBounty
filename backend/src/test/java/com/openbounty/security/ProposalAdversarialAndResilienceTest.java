package com.openbounty.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbounty.dto.request.milestone.MilestoneCreateRequest;
import com.openbounty.dto.request.proposal.ProposalCreateRequest;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.model.Bounty;
import com.openbounty.model.Proposal;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.ProposalRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
 * Adversarial & Stress Testing Suite for Phase 7 (Proposals & Bidding).
 * 
 * Simulates real-world attacker strategies, edge cases, and user misbehaviors:
 * 1. Attacker bidding on an expired/stale bounty past its deadline (Expects 410 Gone / BountyExpiredException).
 * 2. Self-dealing client trying to bid on their own bounty (Expects 400 Bad Request / SelfBiddingNotAllowedException).
 * 3. High-concurrency Race Condition (TOCTOU): Two concurrent clients or requests accepting competing proposals simultaneously.
 * 4. Financial budget violation: Developer bidding 100x the client's bounty reward.
 * 5. High-concurrency double-click: Developer spamming proposal submission to bypass duplicate guard.
 * 6. Cross-tenant IDOR attacks: Malicious client attempting to accept or reject another client's proposal.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ProposalAdversarialAndResilienceTest {

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
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User clientUser;
    private User attackerClientUser;
    private User devUser1;
    private User devUser2;
    private Bounty activeBounty;
    private Bounty expiredBounty;

    private String clientToken;
    private String attackerClientToken;
    private String devToken1;
    private String devToken2;

    @BeforeEach
    void setUp() {
        proposalRepository.deleteAll();
        bountyRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Legitimate Client
        clientUser = userRepository.save(User.builder()
                .name("Legit Client Corp")
                .email("legit@client.com")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_CLIENT)
                .reputationScore(50)
                .build());

        // 2. Malicious Client (Attacker)
        attackerClientUser = userRepository.save(User.builder()
                .name("Evil Corp Hacker")
                .email("evil@hacker.io")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_CLIENT)
                .reputationScore(0)
                .build());

        // 3. Legitimate Developers
        devUser1 = userRepository.save(User.builder()
                .name("Alice Developer")
                .email("alice@dev.org")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(100)
                .build());

        devUser2 = userRepository.save(User.builder()
                .name("Bob Developer")
                .email("bob@dev.org")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(150)
                .build());

        clientToken = jwtService.generateToken(clientUser);
        attackerClientToken = jwtService.generateToken(attackerClientUser);
        devToken1 = jwtService.generateToken(devUser1);
        devToken2 = jwtService.generateToken(devUser2);

        // Active Bounty
        activeBounty = bountyRepository.save(Bounty.builder()
                .title("Build Scalable Payment Gateway")
                .description("Need payment processing with Stripe and PayPal.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("2000.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(14))
                .client(clientUser)
                .build());

        // Expired Bounty (Deadline was 7 days ago)
        expiredBounty = bountyRepository.save(Bounty.builder()
                .title("Ancient Expired Challenge")
                .description("This bounty was posted in the past and the deadline has already expired.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("500.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().minusDays(7))
                .client(clientUser)
                .build());
    }

    // =========================================================================
    // ATTACK 1: Bidding on an Expired / Zombie Bounty
    // =========================================================================
    @Test
    @DisplayName("ATTACK 1: Bidding on an expired bounty should be rejected with 410 GONE (BountyExpiredException)")
    void attack_submitProposalToExpiredBounty_ShouldFailWith410Gone() throws Exception {
        ProposalCreateRequest request = ProposalCreateRequest.builder()
                .approachDescription("I noticed this bounty is expired, but I'm submitting a bid anyway to lock it up.")
                .proposedAmount(new BigDecimal("450.00"))
                .estimatedDays(5)
                .milestones(List.of(
                        MilestoneCreateRequest.builder()
                                .title("Milestone 1: Deliverable")
                                .description("Testing expired bounty submission")
                                .build()
                ))
                .build();

        // An expired bounty MUST NOT accept new proposals! Expected status: 410 GONE (per BountyExpiredException handler)
        mockMvc.perform(post("/api/bounties/" + expiredBounty.getId() + "/proposals")
                        .header("Authorization", "Bearer " + devToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value("https://api.openbounty.dev/errors/bounty-expired"));
    }

    // =========================================================================
    // ATTACK 2: Self-Bidding / Self-Dealing
    // =========================================================================
    @Test
    @DisplayName("ATTACK 2: Client attempting self-bidding should return 400 Bad Request with Self-Dealing Forbidden")
    void attack_clientSelfBidding_ShouldReturn400SelfDealingForbidden() throws Exception {
        // Create user with dual role or developer role who also owns the bounty
        User clientDevUser = userRepository.save(User.builder()
                .name("Sneaky Client Dev")
                .email("sneaky@client.org")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(10)
                .build());

        Bounty clientBounty = bountyRepository.save(Bounty.builder()
                .title("Bounty Created by Sneaky Client")
                .description("Self-dealing test bounty.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1000.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientDevUser)
                .build());

        String sneakyToken = jwtService.generateToken(clientDevUser);

        ProposalCreateRequest request = ProposalCreateRequest.builder()
                .approachDescription("I am bidding on my own bounty to artificially boost my reputation score!")
                .proposedAmount(new BigDecimal("900.00"))
                .estimatedDays(3)
                .milestones(List.of(
                        MilestoneCreateRequest.builder()
                                .title("Milestone 1: Fake Milestone")
                                .build()
                ))
                .build();

        // System specifies SelfBiddingNotAllowedException (HTTP 400 Bad Request, title: 'Self-Dealing Forbidden')
        mockMvc.perform(post("/api/bounties/" + clientBounty.getId() + "/proposals")
                        .header("Authorization", "Bearer " + sneakyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Self-Dealing Forbidden"));
    }

    // =========================================================================
    // ATTACK 3: Financial Budget Violation (Proposing Exorbitant Amount)
    // =========================================================================
    @Test
    @DisplayName("ATTACK 3: Developer proposing amount exceeding the client's bounty reward should be rejected")
    void attack_proposalExceedingBountyReward_ShouldBeRejected() throws Exception {
        // Bounty reward is $2,000.00. Developer asks for $500,000.00!
        ProposalCreateRequest request = ProposalCreateRequest.builder()
                .approachDescription("I want 250 times your posted reward amount.")
                .proposedAmount(new BigDecimal("500000.00"))
                .estimatedDays(14)
                .milestones(List.of(
                        MilestoneCreateRequest.builder()
                                .title("Milestone 1: Ridiculous Bid")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/bounties/" + activeBounty.getId() + "/proposals")
                        .header("Authorization", "Bearer " + devToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // ATTACK 4: TOCTOU Concurrency Attack (Double Acceptance Race Condition)
    // =========================================================================
    @Test
    @DisplayName("ATTACK 4: Concurrent proposal acceptance requests should allow only ONE winner")
    void attack_concurrentProposalAcceptance_ShouldAllowOnlyOneWinner() throws Exception {
        // Create 2 valid proposals for activeBounty
        Proposal p1 = proposalRepository.save(Proposal.builder()
                .bounty(activeBounty)
                .developer(devUser1)
                .approachDescription("Approach 1")
                .proposedAmount(new BigDecimal("1800.00"))
                .estimatedDays(5)
                .status(ProposalStatus.PENDING)
                .build());

        Proposal p2 = proposalRepository.save(Proposal.builder()
                .bounty(activeBounty)
                .developer(devUser2)
                .approachDescription("Approach 2")
                .proposedAmount(new BigDecimal("1900.00"))
                .estimatedDays(6)
                .status(ProposalStatus.PENDING)
                .build());

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        // Request 1: Accept Proposal 1
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(patch("/api/proposals/" + p1.getId() + "/accept")
                                .header("Authorization", "Bearer " + clientToken))
                        .andReturn();
                int httpStatus = result.getResponse().getStatus();
                if (httpStatus == 200) {
                    successCount.incrementAndGet();
                } else if (httpStatus == 409) {
                    conflictCount.incrementAndGet();
                }
            } catch (Exception e) {
                // error
            } finally {
                finishLatch.countDown();
            }
        });

        // Request 2: Accept Proposal 2 simultaneously
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult result = mockMvc.perform(patch("/api/proposals/" + p2.getId() + "/accept")
                                .header("Authorization", "Bearer " + clientToken))
                        .andReturn();
                int httpStatus = result.getResponse().getStatus();
                if (httpStatus == 200) {
                    successCount.incrementAndGet();
                } else if (httpStatus == 409) {
                    conflictCount.incrementAndGet();
                }
            } catch (Exception e) {
                // error
            } finally {
                finishLatch.countDown();
            }
        });

        // Release threads simultaneously to race
        startLatch.countDown();
        boolean completed = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        // Exactly ONE acceptance must succeed; the other must receive 409 Conflict
        assertThat(successCount.get())
                .as("Only ONE acceptance request should succeed")
                .isEqualTo(1);
        assertThat(conflictCount.get())
                .as("Competing concurrent acceptance should be rejected with 409 Conflict")
                .isEqualTo(1);

        // Database invariant check: There can NEVER be more than 1 ACCEPTED proposal for a bounty
        List<Proposal> acceptedProposals = proposalRepository.findByBountyIdAndStatus(activeBounty.getId(), ProposalStatus.ACCEPTED);
        assertThat(acceptedProposals)
                .as("Database must contain exactly 1 accepted proposal")
                .hasSize(1);
    }

    // =========================================================================
    // ATTACK 5: IDOR / Cross-Tenant Proposal Tampering
    // =========================================================================
    @Test
    @DisplayName("ATTACK 5: Malicious client cannot accept another client's proposal via IDOR")
    void attack_maliciousClientAcceptingVictimProposal_ShouldBeBlockedWith403Forbidden() throws Exception {
        Proposal victimProposal = proposalRepository.save(Proposal.builder()
                .bounty(activeBounty)
                .developer(devUser1)
                .approachDescription("Legit proposal for clientUser")
                .proposedAmount(new BigDecimal("1500.00"))
                .estimatedDays(5)
                .status(ProposalStatus.PENDING)
                .build());

        // Attacker attempts to accept victimProposal
        mockMvc.perform(patch("/api/proposals/" + victimProposal.getId() + "/accept")
                        .header("Authorization", "Bearer " + attackerClientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://api.openbounty.dev/errors/access-denied"));

        // Proposal state must remain untouched
        Proposal refreshed = proposalRepository.findById(victimProposal.getId()).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(ProposalStatus.PENDING);
    }
}
