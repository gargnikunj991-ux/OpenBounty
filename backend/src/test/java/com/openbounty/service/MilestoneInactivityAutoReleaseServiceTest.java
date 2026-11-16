package com.openbounty.service;

import com.openbounty.dto.response.ledger.AutoReleaseSummaryResponse;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.model.Bounty;
import com.openbounty.model.Milestone;
import com.openbounty.model.Proposal;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.MilestoneRepository;
import com.openbounty.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MilestoneInactivityAutoReleaseServiceTest {

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private BountyRepository bountyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EscrowLedgerService escrowLedgerService;

    @InjectMocks
    private MilestoneInactivityAutoReleaseService autoReleaseService;

    @Test
    @DisplayName("Should auto-release expired submitted milestone and complete bounty when all milestones approved")
    void testAutoReleaseMilestoneSuccess() {
        User client = User.builder().id(1L).email("client@example.com").build();
        User developer = User.builder().id(2L).email("dev@example.com").reputationScore(50).build();

        Bounty bounty = Bounty.builder()
                .id(10L)
                .title("Test Bounty")
                .client(client)
                .status(BountyStatus.IN_PROGRESS)
                .build();

        Proposal proposal = Proposal.builder()
                .id(20L)
                .bounty(bounty)
                .developer(developer)
                .status(ProposalStatus.ACCEPTED)
                .build();

        Milestone milestone = Milestone.builder()
                .id(100L)
                .title("Deliverable 1")
                .proposal(proposal)
                .status(MilestoneStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now().minusDays(15))
                .build();

        when(milestoneRepository.findByStatusAndSubmittedAtBefore(eq(MilestoneStatus.SUBMITTED), any(LocalDateTime.class)))
                .thenReturn(List.of(milestone));
        when(milestoneRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(milestone));
        when(bountyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(bounty));
        when(milestoneRepository.countByProposalId(20L)).thenReturn(1L);
        when(milestoneRepository.existsByProposalIdAndStatusNot(20L, MilestoneStatus.APPROVED)).thenReturn(false);
        when(userRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(developer));

        AutoReleaseSummaryResponse response = autoReleaseService.sweepAndAutoRelease(14);

        assertThat(response.getProcessedCount()).isEqualTo(1);
        assertThat(response.getAutoReleasedMilestoneIds()).containsExactly(100L);
        assertThat(milestone.getStatus()).isEqualTo(MilestoneStatus.APPROVED);
        assertThat(milestone.getApprovedAt()).isNotNull();
        assertThat(bounty.getStatus()).isEqualTo(BountyStatus.COMPLETED);
        assertThat(developer.getReputationScore()).isEqualTo(70); // 50 + 20

        verify(escrowLedgerService, times(1)).disburseMilestonePayout(milestone, null);
        verify(milestoneRepository, times(1)).save(milestone);
        verify(bountyRepository, times(1)).save(bounty);
        verify(userRepository, times(1)).save(developer);
    }

    @Test
    @DisplayName("Should skip milestone if it is no longer in SUBMITTED state")
    void testSkipIfStatusChanged() {
        Milestone milestone = Milestone.builder()
                .id(101L)
                .status(MilestoneStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now().minusDays(20))
                .build();

        when(milestoneRepository.findByStatusAndSubmittedAtBefore(eq(MilestoneStatus.SUBMITTED), any(LocalDateTime.class)))
                .thenReturn(List.of(milestone));
        // Status changed concurrently to APPROVED
        milestone.setStatus(MilestoneStatus.APPROVED);
        when(milestoneRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(milestone));

        AutoReleaseSummaryResponse response = autoReleaseService.sweepAndAutoRelease(14);

        assertThat(response.getProcessedCount()).isZero();
        verify(escrowLedgerService, never()).disburseMilestonePayout(any(), any());
    }
}
