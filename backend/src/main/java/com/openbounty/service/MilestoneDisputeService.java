package com.openbounty.service;

import com.openbounty.dto.request.milestone.DisputeCreateRequest;
import com.openbounty.dto.request.milestone.DisputeResolveRequest;
import com.openbounty.dto.response.milestone.MilestoneDisputeResponse;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.DisputeDecision;
import com.openbounty.enums.DisputeStatus;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.Role;
import com.openbounty.exception.AccessDeniedException;
import com.openbounty.exception.BadRequestException;
import com.openbounty.exception.DuplicateResourceException;
import com.openbounty.exception.InvalidStateTransitionException;
import com.openbounty.exception.ResourceNotFoundException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Service managing milestone disputes, escrow freezing into suspense, and administrator arbitration settlement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneDisputeService {

    private final MilestoneDisputeRepository disputeRepository;
    private final MilestoneRepository milestoneRepository;
    private final BountyRepository bountyRepository;
    private final UserRepository userRepository;
    private final EscrowLedgerService escrowLedgerService;

    /**
     * Files a formal dispute on a milestone deliverable, freezing escrow into DISPUTE_SUSPENSE.
     */
    @Transactional
    public MilestoneDisputeResponse fileDispute(Long milestoneId, DisputeCreateRequest request, UserPrincipal currentUser) {
        log.info("Dispute requested for milestone ID: {} by user ID: {}", milestoneId, currentUser.getId());

        Milestone milestone = milestoneRepository.findByIdForUpdate(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", "id", milestoneId));

        Proposal proposal = milestone.getProposal();
        if (proposal == null || proposal.getBounty() == null) {
            throw new BadRequestException("Milestone must be associated with an active proposal and bounty.");
        }

        Bounty bounty = proposal.getBounty();

        // 1. IDOR Defense: Only the bounty client or assigned developer can raise a dispute
        Long clientId = bounty.getClient() != null ? bounty.getClient().getId() : null;
        Long devId = proposal.getDeveloper() != null ? proposal.getDeveloper().getId() : null;

        boolean isClient = currentUser.getId().equals(clientId);
        boolean isDev = currentUser.getId().equals(devId);
        boolean isAdmin = currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.name()));

        if (!isClient && !isDev && !isAdmin) {
            log.warn("Unauthorized dispute attempt by user ID {} on milestone ID {}", currentUser.getId(), milestoneId);
            throw new AccessDeniedException("Only the bounty client or assigned developer can raise an arbitration dispute.");
        }

        // 2. State Machine Guards
        if (milestone.getStatus() == MilestoneStatus.APPROVED) {
            throw new BadRequestException("Cannot dispute an already approved deliverable.");
        }
        if (milestone.getStatus() == MilestoneStatus.DISPUTED) {
            throw new BadRequestException("Milestone is already under active arbitration dispute.");
        }

        if (disputeRepository.existsByMilestoneIdAndStatus(milestoneId, DisputeStatus.PENDING_REVIEW)) {
            throw new DuplicateResourceException("An active dispute is already pending review for this milestone.");
        }

        // 3. Freeze escrow in double-entry ledger (DEBIT CLIENT_ESCROW_LOCKED, CREDIT DISPUTE_SUSPENSE)
        LedgerTransaction freezeTxn = escrowLedgerService.freezeMilestoneInDispute(
                milestone, currentUser.getId(), request.getReason());
        BigDecimal frozenAmount = freezeTxn != null ? freezeTxn.calculateTotalDebit() : BigDecimal.ZERO;

        // 4. Update milestone status to DISPUTED
        milestone.setStatus(MilestoneStatus.DISPUTED);
        milestoneRepository.save(milestone);

        // 5. Create dispute record
        User initiator = userRepository.getReferenceById(currentUser.getId());
        MilestoneDispute dispute = MilestoneDispute.builder()
                .milestone(milestone)
                .bounty(bounty)
                .initiator(initiator)
                .reason(request.getReason().trim())
                .evidenceUrl(request.getEvidenceUrl() != null ? request.getEvidenceUrl().trim() : null)
                .status(DisputeStatus.PENDING_REVIEW)
                .frozenAmount(frozenAmount)
                .build();

        MilestoneDispute saved = disputeRepository.save(dispute);
        log.info("Dispute ID {} registered for milestone ID {} with frozen escrow amount {}",
                saved.getId(), milestoneId, frozenAmount);

        return MilestoneDisputeResponse.from(saved);
    }

    /**
     * Resolves an active dispute by administrator decree, releasing suspense escrow.
     */
    @Transactional
    public MilestoneDisputeResponse resolveDispute(Long disputeId, DisputeResolveRequest request, UserPrincipal adminUser) {
        log.info("Admin ID {} resolving dispute ID {} with decision: {}", adminUser.getId(), disputeId, request.getDecision());

        MilestoneDispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneDispute", "id", disputeId));

        if (dispute.getStatus() != DisputeStatus.PENDING_REVIEW) {
            throw new InvalidStateTransitionException("Dispute is already resolved with status: " + dispute.getStatus());
        }

        Milestone milestone = dispute.getMilestone();
        Bounty bounty = dispute.getBounty();

        // 1. Settle ledger funds according to arbitration decision
        escrowLedgerService.resolveDisputeSettlement(dispute, request.getDecision(), adminUser.getId(), request.getResolutionNotes());

        // 2. Map decision to status
        DisputeStatus resolvedStatus;
        if (request.getDecision() == DisputeDecision.REFUND_CLIENT) {
            resolvedStatus = DisputeStatus.RESOLVED_CLIENT_REFUND;
            milestone.setStatus(MilestoneStatus.PENDING); // Reset to pending or allow re-working
        } else if (request.getDecision() == DisputeDecision.DISBURSE_DEVELOPER) {
            resolvedStatus = DisputeStatus.RESOLVED_DEVELOPER_PAYOUT;
            milestone.setStatus(MilestoneStatus.APPROVED);
            milestone.setApprovedAt(LocalDateTime.now());

            // Check auto-completion
            Proposal proposal = milestone.getProposal();
            long totalMilestones = milestoneRepository.countByProposalId(proposal.getId());
            boolean hasUnapproved = milestoneRepository.existsByProposalIdAndStatusNot(proposal.getId(), MilestoneStatus.APPROVED);
            if (!hasUnapproved && totalMilestones > 0 && bounty.getStatus() != BountyStatus.COMPLETED) {
                bounty.setStatus(BountyStatus.COMPLETED);
                bountyRepository.save(bounty);

                User dev = userRepository.findByIdForUpdate(proposal.getDeveloper().getId()).orElse(proposal.getDeveloper());
                dev.setReputationScore(dev.getReputationScore() + 20);
                userRepository.save(dev);
            }
        } else {
            resolvedStatus = DisputeStatus.RESOLVED_SPLIT;
            milestone.setStatus(MilestoneStatus.APPROVED);
            milestone.setApprovedAt(LocalDateTime.now());
        }

        milestoneRepository.save(milestone);

        // 3. Update dispute entity
        User admin = userRepository.getReferenceById(adminUser.getId());
        dispute.setStatus(resolvedStatus);
        dispute.setDecision(request.getDecision());
        dispute.setResolutionNotes(request.getResolutionNotes().trim());
        dispute.setResolvedBy(admin);
        dispute.setResolvedAt(LocalDateTime.now());

        MilestoneDispute updated = disputeRepository.save(dispute);
        log.info("Dispute ID {} successfully resolved as {} by admin ID {}",
                updated.getId(), resolvedStatus, adminUser.getId());

        return MilestoneDisputeResponse.from(updated);
    }

    /**
     * Retrieves a single dispute with tenant IDOR protection.
     */
    @Transactional(readOnly = true)
    public MilestoneDisputeResponse getDisputeById(Long disputeId, UserPrincipal currentUser) {
        MilestoneDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("MilestoneDispute", "id", disputeId));

        Bounty bounty = dispute.getBounty();
        Long clientId = bounty.getClient() != null ? bounty.getClient().getId() : null;
        Long devId = bounty.getAssignedDeveloper() != null ? bounty.getAssignedDeveloper().getId() : null;

        boolean isAuthorized = currentUser.getId().equals(clientId)
                || currentUser.getId().equals(devId)
                || currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.name()));

        if (!isAuthorized) {
            throw new AccessDeniedException("You are not authorized to view this dispute.");
        }

        return MilestoneDisputeResponse.from(dispute);
    }

    /**
     * Lists disputes filtered by role: Admins see all disputes; clients/developers see their own.
     */
    @Transactional(readOnly = true)
    public Page<MilestoneDisputeResponse> getDisputes(UserPrincipal currentUser, Pageable pageable) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.name()));

        Page<MilestoneDispute> page = isAdmin
                ? disputeRepository.findAll(pageable)
                : disputeRepository.findByParticipantUserId(currentUser.getId(), pageable);

        return page.map(MilestoneDisputeResponse::from);
    }
}
