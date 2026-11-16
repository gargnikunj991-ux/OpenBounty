package com.openbounty.service;

import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.model.Bounty;
import com.openbounty.model.Milestone;
import com.openbounty.model.Proposal;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.IdempotencyRecordRepository;
import com.openbounty.repository.LedgerEntryRepository;
import com.openbounty.repository.LedgerTransactionRepository;
import com.openbounty.repository.MilestoneRepository;
import com.openbounty.repository.ProposalRepository;
import com.openbounty.repository.RefreshTokenRepository;
import com.openbounty.repository.ReviewRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.security.JwtService;
import org.junit.jupiter.api.AfterEach;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MilestoneInactivityAutoReleaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BountyRepository bountyRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EscrowLedgerService escrowLedgerService;

    private User client;
    private User developer;
    private User admin;
    private String adminToken;
    private String developerToken;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        client = userRepository.save(User.builder()
                .name("Client Corp")
                .email("client.inactivity@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_CLIENT)
                .reputationScore(100)
                .build());

        developer = userRepository.save(User.builder()
                .name("Dev Solver")
                .email("dev.inactivity@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(50)
                .build());

        admin = userRepository.save(User.builder()
                .name("Admin System")
                .email("admin.inactivity@example.com")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_ADMIN)
                .reputationScore(1000)
                .build());

        adminToken = jwtService.generateToken(admin);
        developerToken = jwtService.generateToken(developer);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        idempotencyRepository.deleteAll();
        entryRepository.deleteAll();
        transactionRepository.deleteAll();
        reviewRepository.deleteAll();
        milestoneRepository.deleteAll();
        proposalRepository.deleteAll();
        bountyRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Non-admin callers should be forbidden from triggering auto-release sweep")
    void testNonAdminForbidden() throws Exception {
        mockMvc.perform(post("/api/ledger/escrow/auto-release-inactivity?days=14")
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin triggering auto-release sweep should approve expired submitted deliverables and disburse escrow")
    void testAdminTriggerAutoReleaseSuccess() throws Exception {
        // 1. Create bounty and lock escrow
        Bounty bounty = bountyRepository.save(Bounty.builder()
                .title("High Concurrency Milestone Auto-Release Task")
                .description("Build automated escrow auto-release mechanism")
                .rewardAmount(new BigDecimal("1000.00"))
                .category(BountyCategory.BACKEND_API)
                .status(BountyStatus.IN_PROGRESS)
                .deadline(LocalDate.now().plusDays(30))
                .client(client)
                .assignedDeveloper(developer)
                .build());

        escrowLedgerService.lockBountyEscrow(bounty);

        // 2. Create accepted proposal
        Proposal proposal = proposalRepository.save(Proposal.builder()
                .bounty(bounty)
                .developer(developer)
                .approachDescription("Full implementation ready")
                .proposedAmount(new BigDecimal("1000.00"))
                .estimatedDays(14)
                .status(ProposalStatus.ACCEPTED)
                .build());

        // 3. Create submitted milestone with submittedAt = 16 days ago
        Milestone expiredMilestone = milestoneRepository.save(Milestone.builder()
                .title("Deliverable 1")
                .description("Production code deliverable")
                .proposal(proposal)
                .status(MilestoneStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now().minusDays(16))
                .deliverableUrl("https://github.com/example/pr/1")
                .build());

        // 4. Trigger auto-release sweep via admin endpoint
        mockMvc.perform(post("/api/ledger/escrow/auto-release-inactivity?days=14")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(1))
                .andExpect(jsonPath("$.thresholdDays").value(14))
                .andExpect(jsonPath("$.autoReleasedMilestoneIds[0]").value(expiredMilestone.getId()));

        // 5. Verify database state
        Milestone updatedMilestone = milestoneRepository.findById(expiredMilestone.getId()).orElseThrow();
        assertThat(updatedMilestone.getStatus()).isEqualTo(MilestoneStatus.APPROVED);
        assertThat(updatedMilestone.getApprovedAt()).isNotNull();

        Bounty updatedBounty = bountyRepository.findById(bounty.getId()).orElseThrow();
        assertThat(updatedBounty.getStatus()).isEqualTo(BountyStatus.COMPLETED);

        User updatedDev = userRepository.findById(developer.getId()).orElseThrow();
        assertThat(updatedDev.getReputationScore()).isEqualTo(70); // 50 + 20 auto-completion bonus

        // Verify ledger developer payable balance is 850 (85% of 1000)
        BigDecimal devPayable = escrowLedgerService.getDeveloperPayableBalance(developer.getId());
        assertThat(devPayable).isEqualByComparingTo(new BigDecimal("850.00"));
    }
}
