package com.openbounty.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbounty.dto.request.milestone.MilestoneSubmitRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MilestoneControllerTest {

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
    private User otherClientUser;
    private User devUser;
    private User attackerDevUser;

    private String clientToken;
    private String otherClientToken;
    private String devToken;
    private String attackerDevToken;

    private Bounty assignedBounty;
    private Proposal acceptedProposal;
    private Milestone milestone1;
    private Milestone milestone2;

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
                .reputationScore(100)
                .build());

        otherClientUser = userRepository.save(User.builder()
                .name("Other Client")
                .email("other@corp.io")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_CLIENT)
                .reputationScore(50)
                .build());

        devUser = userRepository.save(User.builder()
                .name("Alex Solver")
                .email("alex@solver.dev")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(200)
                .build());

        attackerDevUser = userRepository.save(User.builder()
                .name("Attacker Solver")
                .email("attacker@solver.dev")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(10)
                .build());

        clientToken = jwtService.generateToken(clientUser);
        otherClientToken = jwtService.generateToken(otherClientUser);
        devToken = jwtService.generateToken(devUser);
        attackerDevToken = jwtService.generateToken(attackerDevUser);

        assignedBounty = bountyRepository.save(Bounty.builder()
                .title("Build Spring Security 6 Stateless JWT Module")
                .description("Detailed requirement description for JWT auth module.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .status(BountyStatus.ASSIGNED)
                .deadline(LocalDate.now().plusDays(30))
                .client(clientUser)
                .assignedDeveloper(devUser)
                .build());

        acceptedProposal = proposalRepository.save(Proposal.builder()
                .bounty(assignedBounty)
                .developer(devUser)
                .approachDescription("Modular security filter chain.")
                .proposedAmount(new BigDecimal("1500.00"))
                .estimatedDays(14)
                .status(ProposalStatus.ACCEPTED)
                .build());

        milestone1 = milestoneRepository.save(Milestone.builder()
                .proposal(acceptedProposal)
                .title("Milestone 1: JWT Filter Chain")
                .description("JWT signature verification")
                .status(MilestoneStatus.PENDING)
                .build());

        milestone2 = milestoneRepository.save(Milestone.builder()
                .proposal(acceptedProposal)
                .title("Milestone 2: Role Authorization")
                .description("PreAuthorize test suite")
                .status(MilestoneStatus.PENDING)
                .build());
    }

    // =========================================================================
    // SUBMIT DELIVERABLE TESTS
    // =========================================================================

    @Test
    @DisplayName("POST /api/milestones/{id}/submit — Developer submits deliverable proof successfully")
    void submitDeliverable_Success() throws Exception {
        MilestoneSubmitRequest request = MilestoneSubmitRequest.builder()
                .deliverableUrl("https://github.com/alex-dev/openbounty-security/pull/1")
                .notes("All unit and integration tests passing.")
                .build();

        mockMvc.perform(post("/api/milestones/" + milestone1.getId() + "/submit")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(milestone1.getId()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.deliverableUrl").value("https://github.com/alex-dev/openbounty-security/pull/1"))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());

        // Verify database state: bounty transitioned to IN_PROGRESS
        Bounty updatedBounty = bountyRepository.findById(assignedBounty.getId()).orElseThrow();
        assertThat(updatedBounty.getStatus()).isEqualTo(BountyStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("POST /api/milestones/{id}/submit Exploit Blocked — Out-of-order submission rejected with 400")
    void submitDeliverable_OrderViolationExploitBlocked() throws Exception {
        // Milestone 1 is PENDING; developer tries to skip directly to Milestone 2
        MilestoneSubmitRequest request = MilestoneSubmitRequest.builder()
                .deliverableUrl("https://github.com/alex-dev/openbounty-security/pull/2")
                .build();

        mockMvc.perform(post("/api/milestones/" + milestone2.getId() + "/submit")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Milestone Order Violation"))
                .andExpect(jsonPath("$.detail", containsString("must be approved before submitting milestone")));
    }

    @Test
    @DisplayName("POST /api/milestones/{id}/submit Exploit Blocked — IDOR by non-assigned developer returns 403 Forbidden")
    void submitDeliverable_IdorExploitBlocked() throws Exception {
        MilestoneSubmitRequest request = MilestoneSubmitRequest.builder()
                .deliverableUrl("https://github.com/attacker/exploit/pull/1")
                .build();

        mockMvc.perform(post("/api/milestones/" + milestone1.getId() + "/submit")
                        .header("Authorization", "Bearer " + attackerDevToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.detail", containsString("Only the assigned developer can submit milestone deliverables")));
    }

    @Test
    @DisplayName("POST /api/milestones/{id}/submit Exploit Blocked — Malformed URL format returns 400 Bad Request")
    void submitDeliverable_MalformedUrlRejected() throws Exception {
        MilestoneSubmitRequest request = MilestoneSubmitRequest.builder()
                .deliverableUrl("javascript:alert(document.cookie)")
                .build();

        mockMvc.perform(post("/api/milestones/" + milestone1.getId() + "/submit")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    // =========================================================================
    // APPROVE MILESTONE TESTS
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/milestones/{id}/approve — Client approves intermediate milestone")
    void approveMilestone_IntermediateApprovalSuccess() throws Exception {
        milestone1.setStatus(MilestoneStatus.SUBMITTED);
        milestone1.setDeliverableUrl("https://github.com/alex-dev/openbounty-security/pull/1");
        milestoneRepository.save(milestone1);

        mockMvc.perform(patch("/api/milestones/" + milestone1.getId() + "/approve")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(milestone1.getId()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.allMilestonesApproved").value(false))
                .andExpect(jsonPath("$.bountyStatus").value("ASSIGNED"));

        // Bounty remains assigned / in-progress because Milestone 2 is still pending
        Bounty updatedBounty = bountyRepository.findById(assignedBounty.getId()).orElseThrow();
        assertThat(updatedBounty.getStatus()).isNotEqualTo(BountyStatus.COMPLETED);
    }

    @Test
    @DisplayName("PATCH /api/milestones/{id}/approve — Approving 100% of milestones triggers Auto-Completion and awards reputation")
    void approveMilestone_FinalApprovalTriggersAutoCompletion() throws Exception {
        // Milestone 1 is already approved
        milestone1.setStatus(MilestoneStatus.APPROVED);
        milestoneRepository.save(milestone1);

        // Milestone 2 is submitted and waiting for approval
        milestone2.setStatus(MilestoneStatus.SUBMITTED);
        milestone2.setDeliverableUrl("https://github.com/alex-dev/openbounty-security/pull/2");
        milestoneRepository.save(milestone2);

        int initialReputation = devUser.getReputationScore();

        mockMvc.perform(patch("/api/milestones/" + milestone2.getId() + "/approve")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(milestone2.getId()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.allMilestonesApproved").value(true))
                .andExpect(jsonPath("$.bountyStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.message", containsString("All deliverables verified; bounty marked as COMPLETED")));

        // Verify DB auto-completion side effects
        Bounty updatedBounty = bountyRepository.findById(assignedBounty.getId()).orElseThrow();
        assertThat(updatedBounty.getStatus()).isEqualTo(BountyStatus.COMPLETED);

        User updatedDev = userRepository.findById(devUser.getId()).orElseThrow();
        assertThat(updatedDev.getReputationScore()).isEqualTo(initialReputation + 20);
    }

    @Test
    @DisplayName("PATCH /api/milestones/{id}/approve Exploit Blocked — Rogue client IDOR returns 403 Forbidden")
    void approveMilestone_RogueClientIdorBlocked() throws Exception {
        milestone1.setStatus(MilestoneStatus.SUBMITTED);
        milestoneRepository.save(milestone1);

        mockMvc.perform(patch("/api/milestones/" + milestone1.getId() + "/approve")
                        .header("Authorization", "Bearer " + otherClientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.detail", containsString("Only the bounty client owner can approve deliverables")));
    }

    @Test
    @DisplayName("PATCH /api/milestones/{id}/approve — Attempting to approve PENDING milestone returns 409 Conflict")
    void approveMilestone_PendingMilestoneReturnsConflict() throws Exception {
        mockMvc.perform(patch("/api/milestones/" + milestone1.getId() + "/approve")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid State Transition"));
    }

    // =========================================================================
    // REQUEST REVISION TESTS
    // =========================================================================

    @Test
    @DisplayName("POST /api/milestones/{id}/request-revision — Client requests revision; resets milestone to PENDING")
    void requestRevision_Success() throws Exception {
        milestone1.setStatus(MilestoneStatus.SUBMITTED);
        milestone1.setDeliverableUrl("https://github.com/alex-dev/openbounty-security/pull/1");
        milestoneRepository.save(milestone1);

        mockMvc.perform(post("/api/milestones/" + milestone1.getId() + "/request-revision")
                        .header("Authorization", "Bearer " + clientToken)
                        .param("feedbackNotes", "Please add integration tests for JWT expiration."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message", containsString("reverted to PENDING status")));

        Milestone updatedMilestone = milestoneRepository.findById(milestone1.getId()).orElseThrow();
        assertThat(updatedMilestone.getStatus()).isEqualTo(MilestoneStatus.PENDING);
    }

    // =========================================================================
    // GET MILESTONES TESTS
    // =========================================================================

    @Test
    @DisplayName("GET /api/milestones/proposal/{proposalId} — Authorized user lists ordered milestones")
    void getMilestonesByProposal_Success() throws Exception {
        mockMvc.perform(get("/api/milestones/proposal/" + acceptedProposal.getId())
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(milestone1.getId()))
                .andExpect(jsonPath("$[1].id").value(milestone2.getId()));
    }
}
