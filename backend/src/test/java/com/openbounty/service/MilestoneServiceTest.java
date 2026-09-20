package com.openbounty.service;

import com.openbounty.dto.request.milestone.MilestoneSubmitRequest;
import com.openbounty.dto.response.common.ApiResponse;
import com.openbounty.dto.response.milestone.MilestoneApproveResponse;
import com.openbounty.dto.response.milestone.MilestoneResponse;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.exception.AccessDeniedException;
import com.openbounty.exception.BadRequestException;
import com.openbounty.exception.InvalidStateTransitionException;
import com.openbounty.exception.MilestoneOrderViolationException;
import com.openbounty.exception.ResourceNotFoundException;
import com.openbounty.model.Bounty;
import com.openbounty.model.Milestone;
import com.openbounty.model.Proposal;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.MilestoneRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MilestoneServiceTest {

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private BountyRepository bountyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MilestoneService milestoneService;

    private User clientUser;
    private User devUser;
    private User attackerDevUser;
    private User attackerClientUser;

    private UserPrincipal devPrincipal;
    private UserPrincipal attackerDevPrincipal;
    private UserPrincipal clientPrincipal;
    private UserPrincipal attackerClientPrincipal;
    private UserPrincipal adminPrincipal;

    private Bounty bounty;
    private Proposal proposal;
    private Milestone milestone1;
    private Milestone milestone2;

    @BeforeEach
    void setUp() {
        clientUser = User.builder()
                .id(1L)
                .name("Acme Corp")
                .email("client@acme.com")
                .password("hash")
                .role(Role.ROLE_CLIENT)
                .reputationScore(50)
                .build();

        attackerClientUser = User.builder()
                .id(2L)
                .name("Rogue Client")
                .email("rogue@client.com")
                .password("hash")
                .role(Role.ROLE_CLIENT)
                .reputationScore(10)
                .build();

        devUser = User.builder()
                .id(10L)
                .name("Alex Developer")
                .email("alex@dev.com")
                .password("hash")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(100)
                .build();

        attackerDevUser = User.builder()
                .id(99L)
                .name("Attacker Dev")
                .email("attacker@dev.com")
                .password("hash")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(0)
                .build();

        clientPrincipal = UserPrincipal.create(clientUser);
        attackerClientPrincipal = UserPrincipal.create(attackerClientUser);
        devPrincipal = UserPrincipal.create(devUser);
        attackerDevPrincipal = UserPrincipal.create(attackerDevUser);
        adminPrincipal = UserPrincipal.create(User.builder()
                .id(999L)
                .name("Admin")
                .email("admin@openbounty.dev")
                .role(Role.ROLE_ADMIN)
                .password("hash")
                .build());

        bounty = Bounty.builder()
                .id(101L)
                .title("Build Payment Integration")
                .description("Detailed specs")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(BigDecimal.valueOf(1000.00))
                .status(BountyStatus.ASSIGNED)
                .deadline(LocalDate.now().plusDays(14))
                .client(clientUser)
                .assignedDeveloper(devUser)
                .build();

        proposal = Proposal.builder()
                .id(201L)
                .bounty(bounty)
                .developer(devUser)
                .approachDescription("My approach")
                .proposedAmount(BigDecimal.valueOf(1000.00))
                .estimatedDays(7)
                .status(ProposalStatus.ACCEPTED)
                .build();

        milestone1 = Milestone.builder()
                .id(301L)
                .proposal(proposal)
                .title("Milestone 1: Database and API")
                .description("Setup schemas")
                .status(MilestoneStatus.PENDING)
                .build();

        milestone2 = Milestone.builder()
                .id(302L)
                .proposal(proposal)
                .title("Milestone 2: Security & Testing")
                .description("Test suite")
                .status(MilestoneStatus.PENDING)
                .build();
    }

    // =========================================================================
    // SUBMIT DELIVERABLE TESTS & ADVERSARIAL EDGE CASES
    // =========================================================================

    @Test
    @DisplayName("Submit Deliverable: Successfully submits proof and transitions bounty to IN_PROGRESS")
    void submitDeliverable_Success() {
        MilestoneSubmitRequest request = MilestoneSubmitRequest.builder()
                .deliverableUrl("https://github.com/alex-dev/openbounty/pull/1")
                .notes("PR ready for review")
                .build();

        when(milestoneRepository.findWithDetailsById(301L)).thenReturn(Optional.of(milestone1));
        when(milestoneRepository.findByProposalIdOrderByIdAsc(201L)).thenReturn(List.of(milestone1, milestone2));
        when(milestoneRepository.save(any(Milestone.class))).thenAnswer(i -> i.getArgument(0));

        MilestoneResponse response = milestoneService.submitDeliverable(301L, request, devPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(301L);
        assertThat(response.getStatus()).isEqualTo(MilestoneStatus.SUBMITTED);
        assertThat(response.getDeliverableUrl()).isEqualTo("https://github.com/alex-dev/openbounty/pull/1");
        assertThat(bounty.getStatus()).isEqualTo(BountyStatus.IN_PROGRESS);

        verify(bountyRepository).save(bounty);
        verify(milestoneRepository).save(milestone1);
    }

    @Test
    @DisplayName("Submit Deliverable Exploit Blocked: IDOR attempt by non-assigned developer throws AccessDeniedException")
    void submitDeliverable_IdorExploitBlocked() {
        MilestoneSubmitRequest request = MilestoneSubmitRequest.builder()
                .deliverableUrl("https://github.com/attacker/exploit/pull/1")
                .build();

        when(milestoneRepository.findWithDetailsById(301L)).thenReturn(Optional.of(milestone1));

        assertThatThrownBy(() -> milestoneService.submitDeliverable(301L, request, attackerDevPrincipal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the assigned developer can submit milestone deliverables");

        verify(milestoneRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit Deliverable Exploit Blocked: Sequence violation (skipping Milestone 1 to submit Milestone 2) throws MilestoneOrderViolationException")
    void submitDeliverable_OrderViolationExploitBlocked() {
        MilestoneSubmitRequest request = MilestoneSubmitRequest.builder()
                .deliverableUrl("https://github.com/alex-dev/openbounty/pull/2")
                .build();

        // Milestone 1 is still PENDING
        when(milestoneRepository.findWithDetailsById(302L)).thenReturn(Optional.of(milestone2));
        when(milestoneRepository.findByProposalIdOrderByIdAsc(201L)).thenReturn(List.of(milestone1, milestone2));

        assertThatThrownBy(() -> milestoneService.submitDeliverable(302L, request, devPrincipal))
                .isInstanceOf(MilestoneOrderViolationException.class)
                .hasMessageContaining("must be approved before submitting milestone");

        verify(milestoneRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit Deliverable: Attempting submission when milestone is already SUBMITTED throws InvalidStateTransitionException")
    void submitDeliverable_AlreadySubmittedThrowsException() {
        milestone1.setStatus(MilestoneStatus.SUBMITTED);

        MilestoneSubmitRequest request = MilestoneSubmitRequest.builder()
                .deliverableUrl("https://github.com/alex-dev/openbounty/pull/1")
                .build();

        when(milestoneRepository.findWithDetailsById(301L)).thenReturn(Optional.of(milestone1));

        assertThatThrownBy(() -> milestoneService.submitDeliverable(301L, request, devPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class);

        verify(milestoneRepository, never()).save(any());
    }

    @Test
    @DisplayName("Submit Deliverable: Submission rejected if proposal is not ACCEPTED")
    void submitDeliverable_ProposalNotAcceptedThrowsException() {
        proposal.setStatus(ProposalStatus.PENDING);

        MilestoneSubmitRequest request = MilestoneSubmitRequest.builder()
                .deliverableUrl("https://github.com/alex-dev/openbounty/pull/1")
                .build();

        when(milestoneRepository.findWithDetailsById(301L)).thenReturn(Optional.of(milestone1));

        assertThatThrownBy(() -> milestoneService.submitDeliverable(301L, request, devPrincipal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Deliverables can only be submitted for accepted proposals");
    }

    // =========================================================================
    // APPROVE DELIVERABLE TESTS & AUTO-COMPLETION TRIGGERS
    // =========================================================================

    @Test
    @DisplayName("Approve Milestone: Approves intermediate milestone without completing bounty")
    void approveMilestone_IntermediateMilestoneSuccess() {
        milestone1.setStatus(MilestoneStatus.SUBMITTED);

        when(milestoneRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(milestone1));
        when(milestoneRepository.countByProposalId(201L)).thenReturn(2L);
        // Milestone 2 is still unapproved
        when(milestoneRepository.existsByProposalIdAndStatusNot(201L, MilestoneStatus.APPROVED)).thenReturn(true);

        MilestoneApproveResponse response = milestoneService.approveMilestone(301L, clientPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(301L);
        assertThat(response.getStatus()).isEqualTo(MilestoneStatus.APPROVED);
        assertThat(response.isAllMilestonesApproved()).isFalse();
        assertThat(bounty.getStatus()).isEqualTo(BountyStatus.ASSIGNED);

        verify(userRepository, never()).save(any());
        verify(bountyRepository, never()).save(bounty);
    }

    @Test
    @DisplayName("Approve Milestone: Approving 100% of milestones triggers Auto-Completion and awards +20 reputation")
    void approveMilestone_FinalMilestoneTriggersAutoCompletion() {
        milestone2.setStatus(MilestoneStatus.SUBMITTED);

        when(milestoneRepository.findByIdForUpdate(302L)).thenReturn(Optional.of(milestone2));
        when(milestoneRepository.countByProposalId(201L)).thenReturn(2L);
        // No unapproved milestones left
        when(milestoneRepository.existsByProposalIdAndStatusNot(201L, MilestoneStatus.APPROVED)).thenReturn(false);

        int initialReputation = devUser.getReputationScore();

        MilestoneApproveResponse response = milestoneService.approveMilestone(302L, clientPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(MilestoneStatus.APPROVED);
        assertThat(response.isAllMilestonesApproved()).isTrue();
        assertThat(response.getBountyStatus()).isEqualTo(BountyStatus.COMPLETED);
        assertThat(bounty.getStatus()).isEqualTo(BountyStatus.COMPLETED);
        assertThat(devUser.getReputationScore()).isEqualTo(initialReputation + 20);

        verify(bountyRepository).save(bounty);
        verify(userRepository).save(devUser);
    }

    @Test
    @DisplayName("Approve Milestone Exploit Blocked: Rogue client approval throws AccessDeniedException")
    void approveMilestone_RogueClientIdorBlocked() {
        milestone1.setStatus(MilestoneStatus.SUBMITTED);
        when(milestoneRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(milestone1));

        assertThatThrownBy(() -> milestoneService.approveMilestone(301L, attackerClientPrincipal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the bounty client owner can approve deliverables");

        verify(milestoneRepository, never()).save(any());
    }

    @Test
    @DisplayName("Approve Milestone: Cannot approve milestone that is still PENDING")
    void approveMilestone_PendingMilestoneThrowsException() {
        milestone1.setStatus(MilestoneStatus.PENDING);
        when(milestoneRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(milestone1));

        assertThatThrownBy(() -> milestoneService.approveMilestone(301L, clientPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class);

        verify(milestoneRepository, never()).save(any());
    }

    // =========================================================================
    // REQUEST REVISION TESTS
    // =========================================================================

    @Test
    @DisplayName("Request Revision: Successfully resets SUBMITTED milestone back to PENDING")
    void requestRevision_Success() {
        milestone1.setStatus(MilestoneStatus.SUBMITTED);
        when(milestoneRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(milestone1));

        ApiResponse response = milestoneService.requestRevision(301L, "Fix unit tests", clientPrincipal);

        assertThat(response.isSuccess()).isTrue();
        assertThat(milestone1.getStatus()).isEqualTo(MilestoneStatus.PENDING);
        verify(milestoneRepository).save(milestone1);
    }

    @Test
    @DisplayName("Request Revision Exploit Blocked: Non-client caller throws AccessDeniedException")
    void requestRevision_IdorBlocked() {
        milestone1.setStatus(MilestoneStatus.SUBMITTED);
        when(milestoneRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(milestone1));

        assertThatThrownBy(() -> milestoneService.requestRevision(301L, "Fix tests", attackerClientPrincipal))
                .isInstanceOf(AccessDeniedException.class);

        verify(milestoneRepository, never()).save(any());
    }
}
