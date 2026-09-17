package com.openbounty.service;

import com.openbounty.dto.request.milestone.MilestoneCreateRequest;
import com.openbounty.dto.request.proposal.ProposalCreateRequest;
import com.openbounty.dto.response.proposal.ProposalAcceptResponse;
import com.openbounty.dto.response.proposal.ProposalRejectResponse;
import com.openbounty.dto.response.proposal.ProposalResponse;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.exception.AccessDeniedException;
import com.openbounty.exception.DuplicateResourceException;
import com.openbounty.exception.InvalidStateTransitionException;
import com.openbounty.exception.ResourceNotFoundException;
import com.openbounty.exception.SelfBiddingNotAllowedException;
import com.openbounty.model.Bounty;
import com.openbounty.model.Milestone;
import com.openbounty.model.Proposal;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.ProposalRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
class ProposalServiceTest {

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private BountyRepository bountyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProposalService proposalService;

    private User clientUser;
    private User otherClientUser;
    private User devUser;
    private UserPrincipal clientPrincipal;
    private UserPrincipal devPrincipal;
    private UserPrincipal otherClientPrincipal;
    private UserPrincipal adminPrincipal;
    private Bounty sampleBounty;
    private Proposal sampleProposal;

    @BeforeEach
    void setUp() {
        clientUser = User.builder()
                .id(10L)
                .name("Acme Corp")
                .email("client@acme.org")
                .password("hash123")
                .role(Role.ROLE_CLIENT)
                .reputationScore(50)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        otherClientUser = User.builder()
                .id(15L)
                .name("Other Client")
                .email("other@client.org")
                .password("hash123")
                .role(Role.ROLE_CLIENT)
                .reputationScore(30)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        devUser = User.builder()
                .id(20L)
                .name("Dev Solver")
                .email("solver@openbounty.org")
                .password("hash123")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(100)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        clientPrincipal = UserPrincipal.create(clientUser);
        otherClientPrincipal = UserPrincipal.create(otherClientUser);
        devPrincipal = UserPrincipal.create(devUser);
        adminPrincipal = UserPrincipal.fromClaims(99L, "Admin User", "admin@openbounty.org", Role.ROLE_ADMIN);

        sampleBounty = Bounty.builder()
                .id(101L)
                .title("Build Spring Security 6 Module")
                .description("Detailed requirements for JWT auth module.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(30))
                .client(clientUser)
                .assignedDeveloper(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sampleProposal = Proposal.builder()
                .id(201L)
                .bounty(sampleBounty)
                .developer(devUser)
                .approachDescription("I will implement this using JJWT and Spring Security 6.")
                .proposedAmount(new BigDecimal("1400.00"))
                .estimatedDays(7)
                .status(ProposalStatus.PENDING)
                .milestones(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Milestone m1 = Milestone.builder()
                .id(301L)
                .proposal(sampleProposal)
                .title("Milestone 1: SecurityFilterChain")
                .description("Configure filter chain")
                .status(MilestoneStatus.PENDING)
                .build();
        sampleProposal.addMilestone(m1);
    }

    // =========================================================================
    // 1. Submit Proposal Tests
    // =========================================================================

    @Test
    @DisplayName("submitProposal: should successfully create and return proposal with milestones")
    void submitProposal_Success() {
        ProposalCreateRequest request = ProposalCreateRequest.builder()
                .approachDescription("I will implement this using JJWT and Spring Security 6.")
                .proposedAmount(new BigDecimal("1400.00"))
                .estimatedDays(7)
                .milestones(List.of(
                        MilestoneCreateRequest.builder()
                                .title("Milestone 1: SecurityFilterChain")
                                .description("Configure filter chain")
                                .build()
                ))
                .build();

        when(userRepository.findById(devPrincipal.getId())).thenReturn(Optional.of(devUser));
        when(bountyRepository.findById(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));
        when(proposalRepository.existsByBountyIdAndDeveloperId(sampleBounty.getId(), devUser.getId())).thenReturn(false);
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> {
            Proposal p = invocation.getArgument(0);
            p.setId(201L);
            return p;
        });

        ProposalResponse response = proposalService.submitProposal(sampleBounty.getId(), request, devPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(201L);
        assertThat(response.getBountyId()).isEqualTo(sampleBounty.getId());
        assertThat(response.getStatus()).isEqualTo(ProposalStatus.PENDING);
        assertThat(response.getProposedAmount()).isEqualByComparingTo("1400.00");
        assertThat(response.getMilestones()).hasSize(1);
        assertThat(response.getMilestones().get(0).getTitle()).isEqualTo("Milestone 1: SecurityFilterChain");

        verify(proposalRepository).save(any(Proposal.class));
    }

    @Test
    @DisplayName("submitProposal: should throw AccessDeniedException when caller is not ROLE_DEVELOPER")
    void submitProposal_ThrowsAccessDenied_WhenCallerNotDeveloper() {
        ProposalCreateRequest request = ProposalCreateRequest.builder()
                .approachDescription("Valid approach")
                .proposedAmount(new BigDecimal("1000.00"))
                .estimatedDays(5)
                .build();

        when(userRepository.findById(clientPrincipal.getId())).thenReturn(Optional.of(clientUser));

        assertThatThrownBy(() -> proposalService.submitProposal(sampleBounty.getId(), request, clientPrincipal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only developers can submit solution proposals.");

        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitProposal: should throw SelfBiddingNotAllowedException when client bids on their own bounty")
    void submitProposal_ThrowsSelfBiddingNotAllowed_WhenClientBidsOnOwnBounty() {
        User selfBidder = User.builder()
                .id(clientUser.getId()) // Same ID as bounty client
                .name("Self Bidder")
                .email("client@acme.org")
                .role(Role.ROLE_DEVELOPER)
                .build();
        UserPrincipal selfPrincipal = UserPrincipal.create(selfBidder);

        ProposalCreateRequest request = ProposalCreateRequest.builder()
                .approachDescription("Self bid")
                .proposedAmount(new BigDecimal("1000.00"))
                .estimatedDays(5)
                .build();

        when(userRepository.findById(selfPrincipal.getId())).thenReturn(Optional.of(selfBidder));
        when(bountyRepository.findById(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));

        assertThatThrownBy(() -> proposalService.submitProposal(sampleBounty.getId(), request, selfPrincipal))
                .isInstanceOf(SelfBiddingNotAllowedException.class)
                .hasMessageContaining("own bounty");

        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitProposal: should throw InvalidStateTransitionException when bounty is not OPEN or IN_REVIEW")
    void submitProposal_ThrowsInvalidStateTransition_WhenBountyNotOpen() {
        sampleBounty.setStatus(BountyStatus.ASSIGNED);

        ProposalCreateRequest request = ProposalCreateRequest.builder()
                .approachDescription("Valid approach")
                .proposedAmount(new BigDecimal("1000.00"))
                .estimatedDays(5)
                .build();

        when(userRepository.findById(devPrincipal.getId())).thenReturn(Optional.of(devUser));
        when(bountyRepository.findById(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));

        assertThatThrownBy(() -> proposalService.submitProposal(sampleBounty.getId(), request, devPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class);

        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitProposal: should throw DuplicateResourceException when developer already submitted proposal")
    void submitProposal_ThrowsDuplicateResource_WhenAlreadySubmitted() {
        ProposalCreateRequest request = ProposalCreateRequest.builder()
                .approachDescription("Duplicate approach")
                .proposedAmount(new BigDecimal("1000.00"))
                .estimatedDays(5)
                .build();

        when(userRepository.findById(devPrincipal.getId())).thenReturn(Optional.of(devUser));
        when(bountyRepository.findById(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));
        when(proposalRepository.existsByBountyIdAndDeveloperId(sampleBounty.getId(), devUser.getId())).thenReturn(true);

        assertThatThrownBy(() -> proposalService.submitProposal(sampleBounty.getId(), request, devPrincipal))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already submitted a proposal");

        verify(proposalRepository, never()).save(any());
    }

    // =========================================================================
    // 2. Get Proposals for Bounty Tests
    // =========================================================================

    @Test
    @DisplayName("getProposalsByBounty: should return proposals when caller is bounty creator client")
    void getProposalsByBounty_Success_ForOwner() {
        when(bountyRepository.findById(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));
        when(proposalRepository.findByBountyId(sampleBounty.getId())).thenReturn(List.of(sampleProposal));

        List<ProposalResponse> responses = proposalService.getProposalsByBounty(sampleBounty.getId(), clientPrincipal);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(sampleProposal.getId());
        assertThat(responses.get(0).getDeveloper().getName()).isEqualTo("Dev Solver");
    }

    @Test
    @DisplayName("getProposalsByBounty: should allow ROLE_ADMIN to view proposals")
    void getProposalsByBounty_Success_ForAdmin() {
        when(bountyRepository.findById(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));
        when(proposalRepository.findByBountyId(sampleBounty.getId())).thenReturn(List.of(sampleProposal));

        List<ProposalResponse> responses = proposalService.getProposalsByBounty(sampleBounty.getId(), adminPrincipal);

        assertThat(responses).hasSize(1);
    }

    @Test
    @DisplayName("getProposalsByBounty: should throw AccessDeniedException when caller is not the owner")
    void getProposalsByBounty_ThrowsAccessDenied_WhenNotOwner() {
        when(bountyRepository.findById(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));

        assertThatThrownBy(() -> proposalService.getProposalsByBounty(sampleBounty.getId(), otherClientPrincipal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to view proposals for this bounty.");

        verify(proposalRepository, never()).findByBountyId(any());
    }

    // =========================================================================
    // 3. Accept Proposal Tests (Atomic Transaction)
    // =========================================================================

    @Test
    @DisplayName("acceptProposal: should accept proposal, reject competitors, and assign dev to bounty")
    void acceptProposal_Success() {
        when(proposalRepository.findWithDetailsById(sampleProposal.getId())).thenReturn(Optional.of(sampleProposal));
        when(bountyRepository.findByIdForUpdate(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));
        when(proposalRepository.rejectCompetingProposals(sampleBounty.getId(), sampleProposal.getId(), ProposalStatus.REJECTED))
                .thenReturn(2);

        ProposalAcceptResponse response = proposalService.acceptProposal(sampleProposal.getId(), clientPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getProposalId()).isEqualTo(sampleProposal.getId());
        assertThat(response.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(response.getAssignedDeveloperId()).isEqualTo(devUser.getId());
        assertThat(response.getBountyStatus()).isEqualTo(BountyStatus.ASSIGNED);

        assertThat(sampleProposal.getStatus()).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(sampleBounty.getStatus()).isEqualTo(BountyStatus.ASSIGNED);
        assertThat(sampleBounty.getAssignedDeveloper()).isEqualTo(devUser);

        verify(proposalRepository).rejectCompetingProposals(sampleBounty.getId(), sampleProposal.getId(), ProposalStatus.REJECTED);
        verify(bountyRepository).save(sampleBounty);
        verify(proposalRepository).save(sampleProposal);
    }

    @Test
    @DisplayName("acceptProposal: should throw AccessDeniedException when caller is not bounty owner")
    void acceptProposal_ThrowsAccessDenied_WhenNotOwner() {
        when(proposalRepository.findWithDetailsById(sampleProposal.getId())).thenReturn(Optional.of(sampleProposal));
        when(bountyRepository.findByIdForUpdate(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));

        assertThatThrownBy(() -> proposalService.acceptProposal(sampleProposal.getId(), otherClientPrincipal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to accept proposals for this bounty.");

        verify(proposalRepository, never()).rejectCompetingProposals(any(), any(), any());
        verify(bountyRepository, never()).save(any());
    }

    @Test
    @DisplayName("acceptProposal: should throw InvalidStateTransitionException when proposal is not PENDING")
    void acceptProposal_ThrowsInvalidStateTransition_WhenProposalNotPending() {
        sampleProposal.setStatus(ProposalStatus.REJECTED);
        when(proposalRepository.findWithDetailsById(sampleProposal.getId())).thenReturn(Optional.of(sampleProposal));
        when(bountyRepository.findByIdForUpdate(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));

        assertThatThrownBy(() -> proposalService.acceptProposal(sampleProposal.getId(), clientPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class);

        verify(proposalRepository, never()).rejectCompetingProposals(any(), any(), any());
    }

    @Test
    @DisplayName("acceptProposal: should throw InvalidStateTransitionException when bounty is already ASSIGNED")
    void acceptProposal_ThrowsInvalidStateTransition_WhenBountyAlreadyAssigned() {
        sampleBounty.setStatus(BountyStatus.ASSIGNED);
        when(proposalRepository.findWithDetailsById(sampleProposal.getId())).thenReturn(Optional.of(sampleProposal));
        when(bountyRepository.findByIdForUpdate(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));

        assertThatThrownBy(() -> proposalService.acceptProposal(sampleProposal.getId(), clientPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class);

        verify(proposalRepository, never()).rejectCompetingProposals(any(), any(), any());
    }

    // =========================================================================
    // 4. Reject Proposal Tests
    // =========================================================================

    @Test
    @DisplayName("rejectProposal: should mark proposal as REJECTED")
    void rejectProposal_Success() {
        when(proposalRepository.findWithDetailsById(sampleProposal.getId())).thenReturn(Optional.of(sampleProposal));

        ProposalRejectResponse response = proposalService.rejectProposal(sampleProposal.getId(), clientPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getProposalId()).isEqualTo(sampleProposal.getId());
        assertThat(response.getStatus()).isEqualTo(ProposalStatus.REJECTED);
        assertThat(sampleProposal.getStatus()).isEqualTo(ProposalStatus.REJECTED);

        verify(proposalRepository).save(sampleProposal);
    }

    @Test
    @DisplayName("rejectProposal: should throw AccessDeniedException when caller is not bounty owner")
    void rejectProposal_ThrowsAccessDenied_WhenNotOwner() {
        when(proposalRepository.findWithDetailsById(sampleProposal.getId())).thenReturn(Optional.of(sampleProposal));

        assertThatThrownBy(() -> proposalService.rejectProposal(sampleProposal.getId(), otherClientPrincipal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to reject proposals for this bounty.");

        verify(proposalRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejectProposal: should throw InvalidStateTransitionException when proposal is not PENDING")
    void rejectProposal_ThrowsInvalidStateTransition_WhenProposalAlreadyDecided() {
        sampleProposal.setStatus(ProposalStatus.ACCEPTED);
        when(proposalRepository.findWithDetailsById(sampleProposal.getId())).thenReturn(Optional.of(sampleProposal));

        assertThatThrownBy(() -> proposalService.rejectProposal(sampleProposal.getId(), clientPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class);

        verify(proposalRepository, never()).save(any());
    }
}
