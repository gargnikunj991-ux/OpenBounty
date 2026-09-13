package com.openbounty.service;

import com.openbounty.dto.request.milestone.MilestoneCreateRequest;
import com.openbounty.dto.request.proposal.ProposalCreateRequest;
import com.openbounty.dto.response.proposal.ProposalAcceptResponse;
import com.openbounty.dto.response.proposal.ProposalRejectResponse;
import com.openbounty.dto.response.proposal.ProposalResponse;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.exception.AccessDeniedException;
import com.openbounty.exception.BadRequestException;
import com.openbounty.exception.BountyExpiredException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Service managing proposal lifecycle operations:
 * submission by developers, retrieval by bounty owners, atomic acceptance, and rejection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProposalService {

    private final ProposalRepository proposalRepository;
    private final BountyRepository bountyRepository;
    private final UserRepository userRepository;

    /**
     * Submits a technical proposal with structured milestones for an open bounty.
     *
     * @param bountyId    Target bounty identifier
     * @param request     Proposal submission payload
     * @param currentUser Authenticated developer principal
     * @return Detailed proposal response
     */
    @Transactional
    public ProposalResponse submitProposal(Long bountyId, ProposalCreateRequest request, UserPrincipal currentUser) {
        log.info("User '{}' (ID: {}) submitting proposal for bounty ID: {}",
                currentUser.getEmail(), currentUser.getId(), bountyId);

        User developer = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUser.getId()));

        if (developer.getRole() != Role.ROLE_DEVELOPER && developer.getRole() != Role.ROLE_ADMIN) {
            throw new AccessDeniedException("Only developers can submit solution proposals.");
        }

        Bounty bounty = bountyRepository.findById(bountyId)
                .orElseThrow(() -> new ResourceNotFoundException("Bounty", "id", bountyId));

        // FIX 2: Self-dealing guard — use domain-specific exception for fraud detection
        if (bounty.getClient().getId().equals(developer.getId())) {
            log.warn("Self-bidding attempt detected: User ID {} tried to bid on their own bounty ID {}",
                    developer.getId(), bountyId);
            throw new SelfBiddingNotAllowedException("Clients cannot submit proposals to their own bounty.");
        }

        // FIX 1: Deadline expiration guard — reject proposals on expired bounties
        if (bounty.getDeadline() != null && bounty.getDeadline().isBefore(LocalDate.now())) {
            log.warn("Rejected proposal submission: Bounty ID {} deadline ({}) has expired", bountyId, bounty.getDeadline());
            throw new BountyExpiredException("Bounty deadline has expired and cannot accept proposals.");
        }

        // State machine guard: Bounty must be OPEN or IN_REVIEW
        if (bounty.getStatus() != BountyStatus.OPEN && bounty.getStatus() != BountyStatus.IN_REVIEW) {
            log.warn("Rejected proposal submission: Bounty ID {} is in state '{}'", bountyId, bounty.getStatus());
            throw new InvalidStateTransitionException("Bounty", bounty.getStatus(), ProposalStatus.PENDING);
        }

        // FIX 3: Budget cap guard — proposed amount must not exceed bounty reward
        if (request.getProposedAmount().compareTo(bounty.getRewardAmount()) > 0) {
            log.warn("Budget violation: Developer ID {} proposed {} which exceeds bounty reward {} for bounty ID {}",
                    developer.getId(), request.getProposedAmount(), bounty.getRewardAmount(), bountyId);
            throw new BadRequestException(String.format(
                    "Proposed amount (%s) cannot exceed bounty reward amount (%s)",
                    request.getProposedAmount(), bounty.getRewardAmount()
            ));
        }

        // Duplicate bid guard
        if (proposalRepository.existsByBountyIdAndDeveloperId(bountyId, developer.getId())) {
            log.warn("Duplicate proposal attempt: Developer ID {} already bid on bounty ID {}",
                    developer.getId(), bountyId);
            throw new DuplicateResourceException("You have already submitted a proposal for this bounty.");
        }

        Proposal proposal = Proposal.builder()
                .bounty(bounty)
                .developer(developer)
                .approachDescription(request.getApproachDescription().trim())
                .proposedAmount(request.getProposedAmount())
                .estimatedDays(request.getEstimatedDays())
                .status(ProposalStatus.PENDING)
                .build();

        if (request.getMilestones() != null) {
            for (MilestoneCreateRequest milestoneReq : request.getMilestones()) {
                Milestone milestone = Milestone.builder()
                        .title(milestoneReq.getTitle().trim())
                        .description(milestoneReq.getDescription() != null ? milestoneReq.getDescription().trim() : null)
                        .status(MilestoneStatus.PENDING)
                        .build();
                proposal.addMilestone(milestone);
            }
        }

        Proposal savedProposal = proposalRepository.save(proposal);
        log.info("Proposal ID: {} created successfully for bounty ID: {} by developer ID: {}",
                savedProposal.getId(), bountyId, developer.getId());

        return ProposalResponse.from(savedProposal);
    }

    /**
     * Retrieves all proposals submitted for a specific bounty.
     * Only the bounty creator or an administrator may view proposals.
     *
     * @param bountyId    Bounty identifier
     * @param currentUser Authenticated caller
     * @return List of proposal responses
     */
    @Transactional(readOnly = true)
    public List<ProposalResponse> getProposalsByBounty(Long bountyId, UserPrincipal currentUser) {
        log.info("User ID: {} fetching proposals for bounty ID: {}", currentUser.getId(), bountyId);

        Bounty bounty = bountyRepository.findById(bountyId)
                .orElseThrow(() -> new ResourceNotFoundException("Bounty", "id", bountyId));

        verifyBountyOwnership(bounty, currentUser, "view proposals for");

        List<Proposal> proposals = proposalRepository.findByBountyId(bountyId);
        return proposals.stream().map(ProposalResponse::from).toList();
    }

    /**
     * Atomically accepts a winning proposal.
     *
     * FIX 4 (Deadlock Prevention): Acquires a PESSIMISTIC_WRITE lock on the bounty row FIRST
     * via SELECT ... FOR UPDATE. This forces all concurrent acceptance requests to serialize
     * (queue up) on the bounty row, eliminating the cross-lock deadlock where Thread A holds
     * Proposal 1 and waits for Proposal 2, while Thread B holds Proposal 2 and waits for Proposal 1.
     *
     * Side effects (within single transaction):
     * 1. Bounty -> assignedDeveloper = proposal.developer, status = ASSIGNED (locked first)
     * 2. Target proposal -> ACCEPTED
     * 3. Competing proposals for this bounty -> REJECTED (bulk JPQL update)
     *
     * @param proposalId  ID of proposal to accept
     * @param currentUser Authenticated caller (must be bounty owner or admin)
     * @return Confirmation payload
     */
    @Transactional
    public ProposalAcceptResponse acceptProposal(Long proposalId, UserPrincipal currentUser) {
        log.info("Proposal acceptance requested for proposal ID: {} by user ID: {}", proposalId, currentUser.getId());

        Proposal proposal = proposalRepository.findWithDetailsById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("Proposal", "id", proposalId));

        // STEP 1: Lock the bounty row first (SELECT ... FOR UPDATE)
        // This is the critical fix — all concurrent requests must wait here in a queue.
        Bounty bounty = bountyRepository.findByIdForUpdate(proposal.getBounty().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bounty", "id", proposal.getBounty().getId()));

        verifyBountyOwnership(bounty, currentUser, "accept proposals for");

        // Proposal state guard (checks DB directly to bypass 1st-level cache after acquiring lock)
        ProposalStatus proposalStatus = proposalRepository.findStatusById(proposal.getId());
        if (proposalStatus == null) {
            proposalStatus = proposal.getStatus();
        }
        if (proposalStatus != ProposalStatus.PENDING) {
            throw new InvalidStateTransitionException("Proposal", proposalStatus, ProposalStatus.ACCEPTED);
        }

        // Bounty state guard (checks DB directly to bypass 1st-level cache after acquiring lock)
        BountyStatus bountyStatus = bountyRepository.findStatusById(bounty.getId());
        if (bountyStatus == null) {
            bountyStatus = bounty.getStatus();
        }
        if (bountyStatus != BountyStatus.OPEN && bountyStatus != BountyStatus.IN_REVIEW) {
            throw new InvalidStateTransitionException("Bounty", bountyStatus, BountyStatus.ASSIGNED);
        }

        // STEP 2: Update bounty first (we already hold the lock on this row)
        bounty.setAssignedDeveloper(proposal.getDeveloper());
        bounty.setStatus(BountyStatus.ASSIGNED);
        bountyRepository.save(bounty);

        // STEP 3: Mark accepted proposal
        proposal.setStatus(ProposalStatus.ACCEPTED);
        proposalRepository.save(proposal);

        // STEP 4: Reject competing proposals atomically via bulk update
        int rejectedCount = proposalRepository.rejectCompetingProposals(
                bounty.getId(),
                proposal.getId(),
                ProposalStatus.REJECTED
        );
        log.info("Rejected {} competing proposals for bounty ID: {}", rejectedCount, bounty.getId());

        log.info("Proposal ID: {} accepted successfully. Developer ID: {} assigned to bounty ID: {}",
                proposal.getId(), proposal.getDeveloper().getId(), bounty.getId());

        return ProposalAcceptResponse.builder()
                .proposalId(proposal.getId())
                .bountyId(bounty.getId())
                .status(ProposalStatus.ACCEPTED)
                .assignedDeveloperId(proposal.getDeveloper().getId())
                .bountyStatus(bounty.getStatus())
                .message("Proposal accepted successfully. Winning developer assigned.")
                .build();
    }

    /**
     * Explicitly rejects a proposal.
     *
     * @param proposalId  ID of proposal to reject
     * @param currentUser Authenticated caller (must be bounty owner or admin)
     * @return Rejection confirmation payload
     */
    @Transactional
    public ProposalRejectResponse rejectProposal(Long proposalId, UserPrincipal currentUser) {
        log.info("Proposal rejection requested for proposal ID: {} by user ID: {}", proposalId, currentUser.getId());

        Proposal proposal = proposalRepository.findWithDetailsById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("Proposal", "id", proposalId));

        Bounty bounty = proposal.getBounty();
        verifyBountyOwnership(bounty, currentUser, "reject proposals for");

        if (proposal.getStatus() != ProposalStatus.PENDING) {
            throw new InvalidStateTransitionException("Proposal", proposal.getStatus(), ProposalStatus.REJECTED);
        }

        if (bounty.getStatus() == BountyStatus.COMPLETED || bounty.getStatus() == BountyStatus.CANCELLED) {
            throw new InvalidStateTransitionException("Cannot reject proposals on a " + bounty.getStatus() + " bounty.");
        }

        proposal.setStatus(ProposalStatus.REJECTED);
        proposalRepository.save(proposal);

        log.info("Proposal ID: {} successfully rejected by user ID: {}", proposal.getId(), currentUser.getId());

        return ProposalRejectResponse.builder()
                .proposalId(proposal.getId())
                .status(ProposalStatus.REJECTED)
                .build();
    }

    /**
     * Helper to verify that the current user is the owner of the bounty or has administrator privileges.
     */
    private void verifyBountyOwnership(Bounty bounty, UserPrincipal currentUser, String action) {
        boolean isOwner = bounty.getClient().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;

        if (!isOwner && !isAdmin) {
            log.warn("Unauthorized attempt to {} bounty ID: {} by user ID: {}",
                    action, bounty.getId(), currentUser.getId());
            throw new AccessDeniedException("You are not authorized to " + action + " this bounty.");
        }
    }
}
