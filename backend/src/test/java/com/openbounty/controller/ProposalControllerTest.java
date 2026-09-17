package com.openbounty.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbounty.dto.request.milestone.MilestoneCreateRequest;
import com.openbounty.dto.request.proposal.ProposalCreateRequest;
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
import java.util.List;

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
class ProposalControllerTest {

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
    private User devUser1;
    private User devUser2;
    private Bounty openBounty;
    private String clientToken;
    private String otherClientToken;
    private String devToken1;
    private String devToken2;

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
                .reputationScore(40)
                .build());

        devUser1 = userRepository.save(User.builder()
                .name("Alex Solver")
                .email("alex@solver.dev")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(200)
                .build());

        devUser2 = userRepository.save(User.builder()
                .name("Bob Solver")
                .email("bob@solver.dev")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(150)
                .build());

        clientToken = jwtService.generateToken(clientUser);
        otherClientToken = jwtService.generateToken(otherClientUser);
        devToken1 = jwtService.generateToken(devUser1);
        devToken2 = jwtService.generateToken(devUser2);

        openBounty = bountyRepository.save(Bounty.builder()
                .title("Build Spring Security 6 Stateless JWT Module")
                .description("Detailed requirement description for JWT auth module.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(30))
                .client(clientUser)
                .assignedDeveloper(null)
                .build());
    }

    // =========================================================================
    // 1. POST /api/bounties/{id}/proposals
    // =========================================================================

    @Test
    @DisplayName("POST /api/bounties/{id}/proposals: submits proposal successfully with ROLE_DEVELOPER")
    void testSubmitProposal_Success() throws Exception {
        ProposalCreateRequest request = ProposalCreateRequest.builder()
                .approachDescription("I will implement a modular filter chain using JJWT.")
                .proposedAmount(new BigDecimal("1400.00"))
                .estimatedDays(7)
                .milestones(List.of(
                        MilestoneCreateRequest.builder()
                                .title("Milestone 1: Filter Chain Setup")
                                .description("Configure security filter chain")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/bounties/" + openBounty.getId() + "/proposals")
                        .header("Authorization", "Bearer " + devToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.bountyId").value(openBounty.getId()))
                .andExpect(jsonPath("$.developer.id").value(devUser1.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.proposedAmount").value(1400.00))
                .andExpect(jsonPath("$.estimatedDays").value(7))
                .andExpect(jsonPath("$.milestones", hasSize(1)))
                .andExpect(jsonPath("$.milestones[0].title").value("Milestone 1: Filter Chain Setup"));

        assertThat(proposalRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/bounties/{id}/proposals: returns 403 Forbidden when called by ROLE_CLIENT")
    void testSubmitProposal_ForbiddenForClient() throws Exception {
        ProposalCreateRequest request = ProposalCreateRequest.builder()
                .approachDescription("Client attempting to bid.")
                .proposedAmount(new BigDecimal("1400.00"))
                .estimatedDays(7)
                .milestones(List.of(
                        MilestoneCreateRequest.builder()
                                .title("Milestone 1")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/bounties/" + openBounty.getId() + "/proposals")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/bounties/{id}/proposals: returns 401 Unauthorized without token")
    void testSubmitProposal_Unauthorized() throws Exception {
        ProposalCreateRequest request = ProposalCreateRequest.builder()
                .approachDescription("Unauthenticated bid.")
                .proposedAmount(new BigDecimal("1400.00"))
                .estimatedDays(7)
                .build();

        mockMvc.perform(post("/api/bounties/" + openBounty.getId() + "/proposals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/bounties/{id}/proposals: returns 409 Conflict when duplicate proposal submitted")
    void testSubmitProposal_ConflictDuplicate() throws Exception {
        // Create initial proposal
        Proposal p = Proposal.builder()
                .bounty(openBounty)
                .developer(devUser1)
                .approachDescription("Initial proposal")
                .proposedAmount(new BigDecimal("1400.00"))
                .estimatedDays(7)
                .status(ProposalStatus.PENDING)
                .build();
        proposalRepository.save(p);

        ProposalCreateRequest request = ProposalCreateRequest.builder()
                .approachDescription("Second duplicate proposal attempt.")
                .proposedAmount(new BigDecimal("1300.00"))
                .estimatedDays(5)
                .milestones(List.of(
                        MilestoneCreateRequest.builder()
                                .title("Milestone 1")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/bounties/" + openBounty.getId() + "/proposals")
                        .header("Authorization", "Bearer " + devToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You have already submitted a proposal for this bounty."));
    }

    // =========================================================================
    // 2. GET /api/bounties/{id}/proposals
    // =========================================================================

    @Test
    @DisplayName("GET /api/bounties/{id}/proposals: returns proposals to bounty creator client")
    void testGetProposalsByBounty_SuccessForOwner() throws Exception {
        Proposal p = Proposal.builder()
                .bounty(openBounty)
                .developer(devUser1)
                .approachDescription("Quality proposal")
                .proposedAmount(new BigDecimal("1400.00"))
                .estimatedDays(7)
                .status(ProposalStatus.PENDING)
                .build();
        proposalRepository.save(p);

        mockMvc.perform(get("/api/bounties/" + openBounty.getId() + "/proposals")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].developer.name").value("Alex Solver"));
    }

    @Test
    @DisplayName("GET /api/bounties/{id}/proposals: returns 403 Forbidden for non-owner client")
    void testGetProposalsByBounty_ForbiddenForOtherClient() throws Exception {
        mockMvc.perform(get("/api/bounties/" + openBounty.getId() + "/proposals")
                        .header("Authorization", "Bearer " + otherClientToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. PATCH /api/proposals/{id}/accept
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/proposals/{id}/accept: accepts proposal, assigns developer, rejects competing bids")
    void testAcceptProposal_SuccessAtomic() throws Exception {
        // Dev 1 proposal
        Proposal p1 = proposalRepository.save(Proposal.builder()
                .bounty(openBounty)
                .developer(devUser1)
                .approachDescription("Winning proposal")
                .proposedAmount(new BigDecimal("1400.00"))
                .estimatedDays(7)
                .status(ProposalStatus.PENDING)
                .build());

        // Dev 2 competing proposal
        Proposal p2 = proposalRepository.save(Proposal.builder()
                .bounty(openBounty)
                .developer(devUser2)
                .approachDescription("Competing proposal")
                .proposedAmount(new BigDecimal("1500.00"))
                .estimatedDays(10)
                .status(ProposalStatus.PENDING)
                .build());

        mockMvc.perform(patch("/api/proposals/" + p1.getId() + "/accept")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposalId").value(p1.getId()))
                .andExpect(jsonPath("$.bountyId").value(openBounty.getId()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.assignedDeveloperId").value(devUser1.getId()))
                .andExpect(jsonPath("$.bountyStatus").value("ASSIGNED"));

        // Verify database state
        Proposal updatedP1 = proposalRepository.findById(p1.getId()).orElseThrow();
        assertThat(updatedP1.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);

        Proposal updatedP2 = proposalRepository.findById(p2.getId()).orElseThrow();
        assertThat(updatedP2.getStatus()).isEqualTo(ProposalStatus.REJECTED);

        Bounty updatedBounty = bountyRepository.findById(openBounty.getId()).orElseThrow();
        assertThat(updatedBounty.getStatus()).isEqualTo(BountyStatus.ASSIGNED);
        assertThat(updatedBounty.getAssignedDeveloper().getId()).isEqualTo(devUser1.getId());
    }

    @Test
    @DisplayName("PATCH /api/proposals/{id}/accept: returns 403 Forbidden for non-owner client")
    void testAcceptProposal_ForbiddenForOtherClient() throws Exception {
        Proposal p1 = proposalRepository.save(Proposal.builder()
                .bounty(openBounty)
                .developer(devUser1)
                .approachDescription("Proposal")
                .proposedAmount(new BigDecimal("1400.00"))
                .estimatedDays(7)
                .status(ProposalStatus.PENDING)
                .build());

        mockMvc.perform(patch("/api/proposals/" + p1.getId() + "/accept")
                        .header("Authorization", "Bearer " + otherClientToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. PATCH /api/proposals/{id}/reject
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/proposals/{id}/reject: marks proposal as REJECTED")
    void testRejectProposal_Success() throws Exception {
        Proposal p1 = proposalRepository.save(Proposal.builder()
                .bounty(openBounty)
                .developer(devUser1)
                .approachDescription("Proposal to reject")
                .proposedAmount(new BigDecimal("1400.00"))
                .estimatedDays(7)
                .status(ProposalStatus.PENDING)
                .build());

        mockMvc.perform(patch("/api/proposals/" + p1.getId() + "/reject")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposalId").value(p1.getId()))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        Proposal updatedP1 = proposalRepository.findById(p1.getId()).orElseThrow();
        assertThat(updatedP1.getStatus()).isEqualTo(ProposalStatus.REJECTED);
    }
}
