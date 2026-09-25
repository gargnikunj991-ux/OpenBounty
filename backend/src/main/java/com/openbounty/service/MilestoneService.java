package com.openbounty.service;

import com.openbounty.dto.request.milestone.MilestoneSubmitRequest;
import com.openbounty.dto.response.common.ApiResponse;
import com.openbounty.dto.response.milestone.MilestoneApproveResponse;
import com.openbounty.dto.response.milestone.MilestoneResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service managing milestone deliverable verification, sequential submission order,
 * client approval loops, and automated bounty completion triggers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;
    private final ProposalRepository proposalRepository;
    private final BountyRepository bountyRepository;
    private final UserRepository userRepository;

    /**
     * Submits verified deliverable proof for a milestone.
     * Enforces:
     * 1. IDOR guard: Caller must be the assigned developer of the accepted proposal.
     * 2. State guard: Proposal must be ACCEPTED, bounty must be ASSIGNED or IN_PROGRESS.
     * 3. Milestone state guard: Milestone must currently be in PENDING status.
     * 4. Sequential order guard: All preceding milestones must already be APPROVED.
     *
     * Side effect: Automatically transitions bounty from ASSIGNED to IN_PROGRESS on initial submission.
     *
     * @param milestoneId Target milestone identifier
     * @param request     Deliverable proof submission payload (URL + notes)
     * @param currentUser Authenticated caller
     * @return Updated milestone response
     */
    @Transactional
    public MilestoneResponse submitDeliverable(Long milestoneId, MilestoneSubmitRequest request, UserPrincipal currentUser) {
        log.info("Deliverable submission requested for milestone ID: {} by user ID: {}", milestoneId, currentUser.getId());

        Milestone milestone = milestoneRepository.findWithDetailsById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", "id", milestoneId));

        Proposal proposal = milestone.getProposal();
        Bounty bounty = proposal.getBounty();

        // 1. Proposal state check
        if (proposal.getStatus() != ProposalStatus.ACCEPTED) {
            log.warn("Submission rejected: Proposal ID {} is in state '{}' (not ACCEPTED)", proposal.getId(), proposal.getStatus());
            throw new BadRequestException("Deliverables can only be submitted for accepted proposals.");
        }

        // 2. Bounty state check
        if (bounty.getStatus() != BountyStatus.ASSIGNED && bounty.getStatus() != BountyStatus.IN_PROGRESS) {
            log.warn("Submission rejected: Bounty ID {} is in state '{}'", bounty.getId(), bounty.getStatus());
            throw new BadRequestException("Deliverables cannot be submitted for a bounty in status: " + bounty.getStatus());
        }

        // 3. IDOR Defense: Only the assigned developer can submit
        if (!proposal.getDeveloper().getId().equals(currentUser.getId())) {
            log.warn("IDOR attempt: User ID {} attempted to submit deliverable for milestone ID {} owned by developer ID {}",
                    currentUser.getId(), milestoneId, proposal.getDeveloper().getId());
            throw new AccessDeniedException("Only the assigned developer can submit milestone deliverables.");
        }

        // 4. Milestone State Machine Guard: Must be PENDING
        if (milestone.getStatus() != MilestoneStatus.PENDING) {
            log.warn("Submission rejected: Milestone ID {} is already in state '{}'", milestoneId, milestone.getStatus());
            throw new InvalidStateTransitionException("Milestone", milestone.getStatus(), MilestoneStatus.SUBMITTED);
        }

        // 5. Sequential Order Guard: Check preceding milestones
        List<Milestone> proposalMilestones = milestoneRepository.findByProposalIdOrderByIdAsc(proposal.getId());
        for (Milestone precedingMilestone : proposalMilestones) {
            if (precedingMilestone.getId().equals(milestone.getId())) {
                break; // Reached the current milestone
            }
            if (precedingMilestone.getStatus() != MilestoneStatus.APPROVED) {
                log.warn("Milestone sequence violation: Cannot submit milestone ID {} because preceding milestone ID {} has status '{}'",
                        milestone.getId(), precedingMilestone.getId(), precedingMilestone.getStatus());
                throw new MilestoneOrderViolationException(String.format(
                        "Preceding milestone '%s' (ID: %d) must be approved before submitting milestone '%s' (ID: %d).",
                        precedingMilestone.getTitle(), precedingMilestone.getId(), milestone.getTitle(), milestone.getId()
                ));
            }
        }

        // 6. Mutate milestone state
        milestone.setDeliverableUrl(request.getDeliverableUrl().trim());
        milestone.setStatus(MilestoneStatus.SUBMITTED);
        milestone.setSubmittedAt(LocalDateTime.now());
        Milestone savedMilestone = milestoneRepository.save(milestone);

        // 7. Transition bounty from ASSIGNED to IN_PROGRESS on first deliverable submission
        if (bounty.getStatus() == BountyStatus.ASSIGNED) {
            bounty.setStatus(BountyStatus.IN_PROGRESS);
            bountyRepository.save(bounty);
            log.info("Bounty ID: {} transitioned from ASSIGNED to IN_PROGRESS upon first milestone submission", bounty.getId());
        }

        log.info("Milestone ID: {} submitted successfully by developer ID: {}", milestoneId, currentUser.getId());
        return MilestoneResponse.from(savedMilestone);
    }

    /**
     * Approves a submitted milestone deliverable.
     * Enforces:
     * 1. Concurrency lock: Acquires PESSIMISTIC_WRITE lock on the milestone row to prevent double-approval race conditions.
     * 2. IDOR guard: Caller must be the bounty client owner or an admin.
     * 3. State guard: Milestone must currently be in SUBMITTED status.
     *
     * Auto-Completion Trigger:
     * If 100% of milestones for the proposal are APPROVED, transitions the bounty to COMPLETED
     * and awards algorithmic reputation points (+20) to the winning developer.
     *
     * @param milestoneId Target milestone identifier
     * @param currentUser Authenticated caller
     * @return Approval response detailing milestone and bounty statuses
     */
    @Transactional
    public MilestoneApproveResponse approveMilestone(Long milestoneId, UserPrincipal currentUser) {
        log.info("Milestone approval requested for milestone ID: {} by user ID: {}", milestoneId, currentUser.getId());

        // Lock milestone row to eliminate concurrent duplicate approval calls
        Milestone milestone = milestoneRepository.findByIdForUpdate(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", "id", milestoneId));

        Proposal proposal = milestone.getProposal();
        Bounty bounty = proposal.getBounty();

        // 1. IDOR Defense: Only the bounty creator or admin can approve
        boolean isClientOwner = bounty.getClient().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.name()));

        if (!isClientOwner && !isAdmin) {
            log.warn("IDOR attempt: User ID {} tried to approve milestone ID {} belonging to client ID {}",
                    currentUser.getId(), milestoneId, bounty.getClient().getId());
            throw new AccessDeniedException("Only the bounty client owner can approve deliverables.");
        }

        // 2. State Guard: Must be SUBMITTED
        if (milestone.getStatus() != MilestoneStatus.SUBMITTED) {
            log.warn("Approval rejected: Milestone ID {} is in state '{}' (expected SUBMITTED)",
                    milestoneId, milestone.getStatus());
            throw new InvalidStateTransitionException("Milestone", milestone.getStatus(), MilestoneStatus.APPROVED);
        }

        // 3. Update milestone to APPROVED
        milestone.setStatus(MilestoneStatus.APPROVED);
        milestone.setApprovedAt(LocalDateTime.now());
        milestoneRepository.save(milestone);

        // 4. Auto-Completion Check: Verify if all milestones are approved
        long totalMilestones = milestoneRepository.countByProposalId(proposal.getId());
        boolean hasUnapproved = milestoneRepository.existsByProposalIdAndStatusNot(proposal.getId(), MilestoneStatus.APPROVED);
        boolean allMilestonesApproved = !hasUnapproved && totalMilestones > 0;

        BountyStatus finalBountyStatus = bounty.getStatus();
        String message = "Milestone approved successfully.";

        if (allMilestonesApproved) {
            bounty.setStatus(BountyStatus.COMPLETED);
            bountyRepository.save(bounty);
            finalBountyStatus = BountyStatus.COMPLETED;

            // Award developer reputation points (+20 baseline)
            User developer = proposal.getDeveloper();
            developer.setReputationScore(developer.getReputationScore() + 20);
            userRepository.save(developer);

            log.info("Auto-Completion: All {} milestones approved for proposal ID: {}. Bounty ID: {} transitioned to COMPLETED. Developer ID: {} awarded +20 reputation (Total: {}).",
                    totalMilestones, proposal.getId(), bounty.getId(), developer.getId(), developer.getReputationScore());
            message = "Milestone approved. All deliverables verified; bounty marked as COMPLETED.";
        }

        return MilestoneApproveResponse.builder()
                .id(milestone.getId())
                .status(milestone.getStatus())
                .approvedAt(milestone.getApprovedAt())
                .allMilestonesApproved(allMilestonesApproved)
                .bountyStatus(finalBountyStatus)
                .message(message)
                .build();
    }

    /**
     * Requests revisions on a submitted deliverable.
     * Reverts milestone status back to PENDING so the developer can resubmit.
     *
     * @param milestoneId   Target milestone identifier
     * @param feedbackNotes Optional feedback explaining required corrections
     * @param currentUser   Authenticated caller
     * @return Confirmation response
     */
    @Transactional
    public ApiResponse requestRevision(Long milestoneId, String feedbackNotes, UserPrincipal currentUser) {
        log.info("Revision requested for milestone ID: {} by user ID: {}", milestoneId, currentUser.getId());

        Milestone milestone = milestoneRepository.findByIdForUpdate(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", "id", milestoneId));

        Bounty bounty = milestone.getProposal().getBounty();

        boolean isClientOwner = bounty.getClient().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.name()));

        if (!isClientOwner && !isAdmin) {
            log.warn("IDOR attempt: User ID {} tried to request revision on milestone ID {} owned by client ID {}",
                    currentUser.getId(), milestoneId, bounty.getClient().getId());
            throw new AccessDeniedException("Only the bounty client owner can request milestone revisions.");
        }

        if (milestone.getStatus() != MilestoneStatus.SUBMITTED) {
            log.warn("Revision request rejected: Milestone ID {} is in state '{}' (expected SUBMITTED)",
                    milestoneId, milestone.getStatus());
            throw new InvalidStateTransitionException("Milestone", milestone.getStatus(), MilestoneStatus.PENDING);
        }

        milestone.setStatus(MilestoneStatus.PENDING);
        milestoneRepository.save(milestone);

        log.info("Milestone ID: {} reverted to PENDING for revisions. Notes: '{}'", milestoneId, feedbackNotes);
        return ApiResponse.success("Revision requested. Milestone reverted to PENDING status.");
    }

    /**
     * Retrieves a single milestone by ID with access verification.
     *
     * @param milestoneId Milestone ID
     * @param currentUser Authenticated caller
     * @return Milestone response
     */
    @Transactional(readOnly = true)
    public MilestoneResponse getMilestoneById(Long milestoneId, UserPrincipal currentUser) {
        Milestone milestone = milestoneRepository.findWithDetailsById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", "id", milestoneId));

        verifyMilestoneAccess(milestone, currentUser);
        return MilestoneResponse.from(milestone);
    }

    /**
     * Retrieves all milestones for a proposal in chronological/sequence order.
     *
     * @param proposalId  Target proposal ID
     * @param currentUser Authenticated caller
     * @return Ordered list of milestone responses
     */
    @Transactional(readOnly = true)
    public List<MilestoneResponse> getMilestonesByProposal(Long proposalId, UserPrincipal currentUser) {
        Proposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("Proposal", "id", proposalId));

        boolean isDeveloper = proposal.getDeveloper().getId().equals(currentUser.getId());
        boolean isClient = proposal.getBounty().getClient().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.name()));

        if (!isDeveloper && !isClient && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to view milestones for this proposal.");
        }

        List<Milestone> milestones = milestoneRepository.findByProposalIdOrderByIdAsc(proposalId);
        return milestones.stream().map(MilestoneResponse::from).toList();
    }

    private void verifyMilestoneAccess(Milestone milestone, UserPrincipal currentUser) {
        Proposal proposal = milestone.getProposal();
        boolean isDeveloper = proposal.getDeveloper().getId().equals(currentUser.getId());
        boolean isClient = proposal.getBounty().getClient().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(Role.ROLE_ADMIN.name()));

        if (!isDeveloper && !isClient && !isAdmin) {
            throw new AccessDeniedException("You do not have permission to view this milestone.");
        }
    }
}
