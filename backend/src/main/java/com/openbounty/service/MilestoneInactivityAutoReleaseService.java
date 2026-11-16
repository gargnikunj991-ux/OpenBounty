package com.openbounty.service;

import com.openbounty.dto.response.ledger.AutoReleaseSummaryResponse;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.model.Bounty;
import com.openbounty.model.Milestone;
import com.openbounty.model.Proposal;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.MilestoneRepository;
import com.openbounty.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service orchestrating 14-day inactivity escrow auto-release.
 * Safeguards developers against unresponsive/abandoned client reviews by automatically
 * approving milestones and disbursing escrow when the review period lapses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneInactivityAutoReleaseService {

    private final MilestoneRepository milestoneRepository;
    private final BountyRepository bountyRepository;
    private final UserRepository userRepository;
    private final EscrowLedgerService escrowLedgerService;

    @Value("${openbounty.escrow.inactivity-auto-release.threshold-days:14}")
    private int configuredThresholdDays;

    /**
     * Executes the inactivity sweep using the configured threshold days.
     */
    @Transactional
    public AutoReleaseSummaryResponse sweepAndAutoRelease() {
        return sweepAndAutoRelease(configuredThresholdDays);
    }

    /**
     * Finds and auto-releases all submitted deliverables that have remained unreviewed
     * for longer than the specified threshold days.
     */
    @Transactional
    public AutoReleaseSummaryResponse sweepAndAutoRelease(int thresholdDays) {
        LocalDateTime thresholdTime = LocalDateTime.now().minusDays(thresholdDays);
        log.info("Starting inactivity auto-release sweep for milestones submitted before {} (threshold: {} days)",
                thresholdTime, thresholdDays);

        List<Milestone> expiredMilestones = milestoneRepository.findByStatusAndSubmittedAtBefore(
                MilestoneStatus.SUBMITTED, thresholdTime);

        List<Long> autoReleasedIds = new ArrayList<>();

        for (Milestone m : expiredMilestones) {
            try {
                boolean released = processMilestoneAutoRelease(m.getId(), thresholdDays);
                if (released) {
                    autoReleasedIds.add(m.getId());
                }
            } catch (Exception ex) {
                log.error("Failed to auto-release milestone ID {} during inactivity sweep: {}", m.getId(), ex.getMessage(), ex);
            }
        }

        String message = String.format("Inactivity auto-release sweep completed. Processed %d milestones.", autoReleasedIds.size());
        log.info(message);

        return AutoReleaseSummaryResponse.builder()
                .processedCount(autoReleasedIds.size())
                .thresholdDays(thresholdDays)
                .autoReleasedMilestoneIds(autoReleasedIds)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Processes a single milestone auto-release inside its own transactional / locking scope.
     */
    @Transactional
    public boolean processMilestoneAutoRelease(Long milestoneId, int thresholdDays) {
        Milestone milestone = milestoneRepository.findByIdForUpdate(milestoneId).orElse(null);
        if (milestone == null || milestone.getStatus() != MilestoneStatus.SUBMITTED) {
            log.debug("Milestone ID {} no longer in SUBMITTED state. Skipping auto-release.", milestoneId);
            return false;
        }

        Proposal proposal = milestone.getProposal();
        if (proposal == null || proposal.getBounty() == null) {
            log.warn("Milestone ID {} has missing proposal or bounty. Skipping.", milestoneId);
            return false;
        }

        Bounty bounty = bountyRepository.findByIdForUpdate(proposal.getBounty().getId())
                .orElse(proposal.getBounty());

        // 1. Disburse escrow via double-entry financial ledger
        escrowLedgerService.disburseMilestonePayout(milestone, null);

        // 2. Transition milestone status to APPROVED
        milestone.setStatus(MilestoneStatus.APPROVED);
        milestone.setApprovedAt(LocalDateTime.now());
        milestoneRepository.save(milestone);

        log.info("Auto-released milestone ID {} due to client inactivity ({} days lapse). Disbursed escrow.",
                milestoneId, thresholdDays);

        // 3. Auto-completion trigger: check if 100% of milestones are approved
        long totalMilestones = milestoneRepository.countByProposalId(proposal.getId());
        boolean hasUnapproved = milestoneRepository.existsByProposalIdAndStatusNot(proposal.getId(), MilestoneStatus.APPROVED);
        boolean allMilestonesApproved = !hasUnapproved && totalMilestones > 0;

        if (allMilestonesApproved && bounty.getStatus() != BountyStatus.COMPLETED) {
            bounty.setStatus(BountyStatus.COMPLETED);
            bountyRepository.save(bounty);

            // Award developer reputation bonus (+20) with lock
            User developer = userRepository.findByIdForUpdate(proposal.getDeveloper().getId())
                    .orElse(proposal.getDeveloper());
            developer.setReputationScore(developer.getReputationScore() + 20);
            userRepository.save(developer);

            log.info("Auto-Completion: All {} milestones approved for proposal ID {}. Bounty ID {} COMPLETED via auto-release. Developer ID {} awarded +20 reputation (Total: {}).",
                    totalMilestones, proposal.getId(), bounty.getId(), developer.getId(), developer.getReputationScore());
        }

        return true;
    }
}
