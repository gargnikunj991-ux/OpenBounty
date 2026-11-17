package com.openbounty.service;

import com.openbounty.dto.request.milestone.DisputeCreateRequest;
import com.openbounty.dto.request.milestone.DisputeResolveRequest;
import com.openbounty.dto.response.milestone.MilestoneDisputeResponse;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.DisputeDecision;
import com.openbounty.enums.DisputeStatus;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.exception.AccessDeniedException;
import com.openbounty.exception.BadRequestException;
import com.openbounty.model.Bounty;
import com.openbounty.model.LedgerTransaction;
import com.openbounty.model.Milestone;
import com.openbounty.model.MilestoneDispute;
import com.openbounty.model.Proposal;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.MilestoneDisputeRepository;
import com.openbounty.repository.MilestoneRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MilestoneDisputeServiceTest {

    @Mock
    private MilestoneDisputeRepository disputeRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private BountyRepository bountyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EscrowLedgerService escrowLedgerService;

    @InjectMocks
    private MilestoneDisputeService disputeService;

    private User clientUser;
    private User devUser;
    private User adminUser;
    private UserPrincipal clientPrincipal;
    private UserPrincipal devPrincipal;
    private UserPrincipal adminPrincipal;
    private Bounty bounty;
    private Proposal proposal;
    private Milestone milestone;

    @BeforeEach
    void setUp() {
        clientUser = User.builder().id(10L).name("Client").email("client@test.com").role(Role.ROLE_CLIENT).build();
        devUser = User.builder().id(20L).name("Developer").email("dev@test.com").role(Role.ROLE_DEVELOPER).reputationScore(50).build();
        adminUser = User.builder().id(99L).name("Admin").email("admin@test.com").role(Role.ROLE_ADMIN).build();

        clientPrincipal = UserPrincipal.create(clientUser);
        devPrincipal = UserPrincipal.create(devUser);
        adminPrincipal = UserPrincipal.create(adminUser);

        bounty = Bounty.builder()
                .id(100L)
                .title("Dispute Test Bounty")
                .client(clientUser)
                .assignedDeveloper(devUser)
                .status(BountyStatus.IN_PROGRESS)
                .build();

        proposal = Proposal.builder()
                .id(200L)
                .bounty(bounty)
                .developer(devUser)
                .status(ProposalStatus.ACCEPTED)
                .build();

        milestone = Milestone.builder()
                .id(300L)
                .title("Disputable Deliverable")
                .proposal(proposal)
                .status(MilestoneStatus.SUBMITTED)
                .build();
    }

    @Test
    @DisplayName("Client should successfully file dispute and freeze milestone escrow into suspense")
    void testFileDisputeSuccess() {
        when(milestoneRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(milestone));
        when(disputeRepository.existsByMilestoneIdAndStatus(300L, DisputeStatus.PENDING_REVIEW)).thenReturn(false);

        LedgerTransaction freezeTxn = LedgerTransaction.builder()
                .transactionReference("TXN-FREEZE-300")
                .build();
        when(escrowLedgerService.freezeMilestoneInDispute(eq(milestone), eq(10L), any())).thenReturn(freezeTxn);
        when(userRepository.getReferenceById(10L)).thenReturn(clientUser);

        when(disputeRepository.save(any(MilestoneDispute.class))).thenAnswer(i -> {
            MilestoneDispute d = i.getArgument(0);
            d.setId(500L);
            return d;
        });

        DisputeCreateRequest req = new DisputeCreateRequest("Deliverable does not meet acceptance criteria", "https://evidence.test");
        MilestoneDisputeResponse res = disputeService.fileDispute(300L, req, clientPrincipal);

        assertThat(res).isNotNull();
        assertThat(res.getStatus()).isEqualTo(DisputeStatus.PENDING_REVIEW);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.DISPUTED);
        verify(escrowLedgerService, times(1)).freezeMilestoneInDispute(milestone, 10L, req.getReason());
        verify(milestoneRepository, times(1)).save(milestone);
    }

    @Test
    @DisplayName("Unauthorized third party filing dispute should be rejected with AccessDeniedException")
    void testFileDisputeUnauthorized() {
        User strangerUser = User.builder().id(999L).name("Stranger").email("stranger@test.com").role(Role.ROLE_DEVELOPER).build();
        UserPrincipal stranger = UserPrincipal.create(strangerUser);
        when(milestoneRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(milestone));

        DisputeCreateRequest req = new DisputeCreateRequest("Not my business", null);
        assertThatThrownBy(() -> disputeService.fileDispute(300L, req, stranger))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the bounty client or assigned developer");
    }

    @Test
    @DisplayName("Cannot dispute an already approved deliverable")
    void testDisputeAlreadyApprovedFails() {
        milestone.setStatus(MilestoneStatus.APPROVED);
        when(milestoneRepository.findByIdForUpdate(300L)).thenReturn(Optional.of(milestone));

        DisputeCreateRequest req = new DisputeCreateRequest("Late complaint", null);
        assertThatThrownBy(() -> disputeService.fileDispute(300L, req, clientPrincipal))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot dispute an already approved deliverable");
    }

    @Test
    @DisplayName("Admin should resolve dispute with DISBURSE_DEVELOPER, completing bounty and awarding reputation")
    void testAdminResolveDisburseDeveloper() {
        MilestoneDispute dispute = MilestoneDispute.builder()
                .id(501L)
                .milestone(milestone)
                .bounty(bounty)
                .initiator(clientUser)
                .status(DisputeStatus.PENDING_REVIEW)
                .frozenAmount(new BigDecimal("500.00"))
                .build();

        when(disputeRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(dispute));
        when(userRepository.getReferenceById(99L)).thenReturn(adminUser);
        when(milestoneRepository.countByProposalId(200L)).thenReturn(1L);
        when(milestoneRepository.existsByProposalIdAndStatusNot(200L, MilestoneStatus.APPROVED)).thenReturn(false);
        when(userRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(devUser));
        when(disputeRepository.save(any(MilestoneDispute.class))).thenAnswer(i -> i.getArgument(0));

        DisputeResolveRequest req = new DisputeResolveRequest(DisputeDecision.DISBURSE_DEVELOPER, "Deliverable verified valid.");
        MilestoneDisputeResponse res = disputeService.resolveDispute(501L, req, adminPrincipal);

        assertThat(res.getStatus()).isEqualTo(DisputeStatus.RESOLVED_DEVELOPER_PAYOUT);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.APPROVED);
        assertThat(bounty.getStatus()).isEqualTo(BountyStatus.COMPLETED);
        assertThat(devUser.getReputationScore()).isEqualTo(70); // 50 + 20

        verify(escrowLedgerService, times(1)).resolveDisputeSettlement(dispute, DisputeDecision.DISBURSE_DEVELOPER, 99L, req.getResolutionNotes());
    }

    @Test
    @DisplayName("Admin should resolve dispute with REFUND_CLIENT, returning funds to client")
    void testAdminResolveRefundClient() {
        MilestoneDispute dispute = MilestoneDispute.builder()
                .id(502L)
                .milestone(milestone)
                .bounty(bounty)
                .initiator(clientUser)
                .status(DisputeStatus.PENDING_REVIEW)
                .frozenAmount(new BigDecimal("500.00"))
                .build();

        when(disputeRepository.findByIdForUpdate(502L)).thenReturn(Optional.of(dispute));
        when(userRepository.getReferenceById(99L)).thenReturn(adminUser);
        when(disputeRepository.save(any(MilestoneDispute.class))).thenAnswer(i -> i.getArgument(0));

        DisputeResolveRequest req = new DisputeResolveRequest(DisputeDecision.REFUND_CLIENT, "Deliverable not provided.");
        MilestoneDisputeResponse res = disputeService.resolveDispute(502L, req, adminPrincipal);

        assertThat(res.getStatus()).isEqualTo(DisputeStatus.RESOLVED_CLIENT_REFUND);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.PENDING);
        verify(escrowLedgerService, times(1)).resolveDisputeSettlement(dispute, DisputeDecision.REFUND_CLIENT, 99L, req.getResolutionNotes());
    }
}
