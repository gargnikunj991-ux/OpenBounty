package com.openbounty.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbounty.dto.request.milestone.DisputeCreateRequest;
import com.openbounty.dto.request.milestone.DisputeResolveRequest;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.DisputeDecision;
import com.openbounty.enums.DisputeStatus;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.model.Bounty;
import com.openbounty.model.Milestone;
import com.openbounty.model.MilestoneDispute;
import com.openbounty.model.Proposal;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.IdempotencyRecordRepository;
import com.openbounty.repository.LedgerEntryRepository;
import com.openbounty.repository.LedgerTransactionRepository;
import com.openbounty.repository.MilestoneDisputeRepository;
import com.openbounty.repository.MilestoneRepository;
import com.openbounty.repository.ProposalRepository;
import com.openbounty.repository.RefreshTokenRepository;
import com.openbounty.repository.ReviewRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.security.JwtService;
import com.openbounty.service.EscrowLedgerService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DisputeControllerIntegrationTest {

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
    private MilestoneDisputeRepository disputeRepository;

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
    private User stranger;
    private User admin;
    private String clientToken;
    private String developerToken;
    private String strangerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        client = userRepository.save(User.builder()
                .name("Dispute Client")
                .email("client.disp@example.com")
                .password(passwordEncoder.encode("Pass123!"))
                .role(Role.ROLE_CLIENT)
                .reputationScore(100)
                .build());

        developer = userRepository.save(User.builder()
                .name("Dispute Dev")
                .email("dev.disp@example.com")
                .password(passwordEncoder.encode("Pass123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(50)
                .build());

        stranger = userRepository.save(User.builder()
                .name("Stranger")
                .email("stranger.disp@example.com")
                .password(passwordEncoder.encode("Pass123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(10)
                .build());

        admin = userRepository.save(User.builder()
                .name("Admin Arbitrator")
                .email("admin.disp@example.com")
                .password(passwordEncoder.encode("Pass123!"))
                .role(Role.ROLE_ADMIN)
                .reputationScore(1000)
                .build());

        clientToken = jwtService.generateToken(client);
        developerToken = jwtService.generateToken(developer);
        strangerToken = jwtService.generateToken(stranger);
        adminToken = jwtService.generateToken(admin);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        disputeRepository.deleteAll();
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
    @DisplayName("End-to-End Dispute Workflow: File dispute, check IDOR, admin settle split 50/50")
    void testDisputeLifecycleAndAdminSplitSettlement() throws Exception {
        // 1. Setup Bounty, Escrow, and Milestone
        Bounty bounty = bountyRepository.save(Bounty.builder()
                .title("Full Stack Web Platform with Escrow")
                .description("Build complex platform with milestones")
                .rewardAmount(new BigDecimal("2000.00"))
                .category(BountyCategory.WEB_DEVELOPMENT)
                .status(BountyStatus.IN_PROGRESS)
                .deadline(LocalDate.now().plusDays(30))
                .client(client)
                .assignedDeveloper(developer)
                .build());

        escrowLedgerService.lockBountyEscrow(bounty);

        Proposal proposal = proposalRepository.save(Proposal.builder()
                .bounty(bounty)
                .developer(developer)
                .approachDescription("Comprehensive architecture")
                .proposedAmount(new BigDecimal("2000.00"))
                .estimatedDays(20)
                .status(ProposalStatus.ACCEPTED)
                .build());

        Milestone milestone = milestoneRepository.save(Milestone.builder()
                .title("Milestone 1: Backend Architecture")
                .description("API and database schemas")
                .proposal(proposal)
                .status(MilestoneStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .deliverableUrl("https://github.com/example/pr/100")
                .build());

        // 2. Client files dispute
        DisputeCreateRequest disputeRequest = new DisputeCreateRequest(
                "Code quality does not meet enterprise requirements", "https://evidence.example.com/log");

        String responseJson = mockMvc.perform(post("/api/milestones/" + milestone.getId() + "/dispute")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disputeRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.milestoneId").value(milestone.getId()))
                .andReturn().getResponse().getContentAsString();

        Long disputeId = objectMapper.readTree(responseJson).get("id").asLong();

        // Verify milestone status is DISPUTED
        Milestone disputedMilestone = milestoneRepository.findById(milestone.getId()).orElseThrow();
        assertThat(disputedMilestone.getStatus()).isEqualTo(MilestoneStatus.DISPUTED);

        // 3. IDOR Defense: Stranger cannot view dispute details
        mockMvc.perform(get("/api/disputes/" + disputeId)
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        // Client and Developer can view
        mockMvc.perform(get("/api/disputes/" + disputeId)
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(disputeId));

        // 4. Non-admin cannot resolve dispute
        DisputeResolveRequest resolveRequest = new DisputeResolveRequest(
                DisputeDecision.SPLIT_50_50, "Mutual partial delivery verified.");

        mockMvc.perform(post("/api/admin/disputes/" + disputeId + "/resolve")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveRequest)))
                .andExpect(status().isForbidden());

        // 5. Admin resolves dispute with SPLIT_50_50
        mockMvc.perform(post("/api/admin/disputes/" + disputeId + "/resolve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED_SPLIT"))
                .andExpect(jsonPath("$.decision").value("SPLIT_50_50"));

        // 6. Verify ledger balances after split
        // Gross milestone was 2000. 1000 refunded to client, 1000 split for dev: 850 payable (85%) + 150 platform fee (15%)
        BigDecimal devBalance = escrowLedgerService.getDeveloperPayableBalance(developer.getId());
        assertThat(devBalance).isEqualByComparingTo(new BigDecimal("850.00"));

        MilestoneDispute settled = disputeRepository.findById(disputeId).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(DisputeStatus.RESOLVED_SPLIT);
    }
}
