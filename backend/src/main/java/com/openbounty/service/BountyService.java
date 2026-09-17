package com.openbounty.service;

import com.openbounty.dto.request.bounty.BountyCreateRequest;
import com.openbounty.dto.response.bounty.BountyCancelResponse;
import com.openbounty.dto.response.bounty.BountyResponse;
import com.openbounty.dto.response.bounty.BountySummaryResponse;
import com.openbounty.dto.response.common.PagedResponse;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.exception.AccessDeniedException;
import com.openbounty.exception.BadRequestException;
import com.openbounty.exception.InvalidStateTransitionException;
import com.openbounty.exception.ResourceNotFoundException;
import com.openbounty.model.Bounty;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.ProposalRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Service managing the Bounty / Challenge lifecycle, including creation,
 * discovery searching/filtering, detail retrieval, and cancellation guards.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BountyService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "title", "rewardAmount", "deadline", "status", "category", "createdAt", "updatedAt"
    );

    private final BountyRepository bountyRepository;
    private final UserRepository userRepository;
    private final ProposalRepository proposalRepository;

    /**
     * Creates a new technical challenge posted by an authenticated client.
     *
     * @param request     Validated bounty creation payload
     * @param currentUser Authenticated security principal
     * @return Detailed representation of the created bounty
     */
    @Transactional
    public BountyResponse createBounty(BountyCreateRequest request, UserPrincipal currentUser) {
        log.info("User '{}' (ID: {}) creating new bounty with title: '{}'",
                currentUser.getEmail(), currentUser.getId(), request.getTitle());

        validateNoMaliciousContent(request.getTitle(), "title");
        validateNoMaliciousContent(request.getDescription(), "description");

        User client = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUser.getId()));

        if (client.getRole() != Role.ROLE_CLIENT && client.getRole() != Role.ROLE_ADMIN) {
            throw new AccessDeniedException("Only clients can create technical bounties.");
        }

        Bounty bounty = Bounty.builder()
                .title(request.getTitle().trim())
                .description(request.getDescription().trim())
                .category(request.getCategory())
                .rewardAmount(request.getRewardAmount())
                .deadline(request.getDeadline())
                .status(BountyStatus.OPEN)
                .client(client)
                .assignedDeveloper(null)
                .build();

        Bounty savedBounty = bountyRepository.save(bounty);
        log.info("Bounty created successfully with ID: {} by client ID: {}", savedBounty.getId(), client.getId());

        return BountyResponse.from(savedBounty);
    }

    /**
     * Paginated and filtered lookup of bounties in the marketplace.
     * Supports filtering by status, category, and keyword matching.
     *
     * @param status   Optional lifecycle status filter
     * @param category Optional category filter
     * @param search   Optional free-text keyword matching title and description
     * @param pageable Pagination and sorting parameters
     * @return Standardized paginated envelope containing compact bounty cards
     */
    @Transactional(readOnly = true)
    public PagedResponse<BountySummaryResponse> getBounties(
            BountyStatus status,
            BountyCategory category,
            String search,
            Pageable pageable
    ) {
        if (pageable != null && pageable.getSort().isSorted()) {
            for (Sort.Order order : pageable.getSort()) {
                if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    log.warn("Rejected invalid or sensitive sort parameter: '{}'", order.getProperty());
                    throw new BadRequestException("Invalid sort field: '" + order.getProperty() + "'. Allowed sort fields are: " + ALLOWED_SORT_FIELDS);
                }
            }
        }

        String keyword = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<Bounty> page = bountyRepository.searchBounties(status, category, keyword, pageable);
        return PagedResponse.from(page, BountySummaryResponse::from);
    }

    /**
     * Retrieve complete details of a single bounty by its ID.
     * Eagerly fetches relationships to eliminate N+1 queries.
     *
     * @param id Bounty unique identifier
     * @return Full detailed representation of the bounty
     */
    @Transactional(readOnly = true)
    public BountyResponse getBountyById(Long id) {
        Bounty bounty = bountyRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bounty", "id", id));

        return BountyResponse.from(bounty);
    }

    /**
     * Cancels an open bounty with strict state-machine and ownership validation.
     * Only the bounty owner (or an administrator) can cancel.
     * A bounty cannot be cancelled once a developer is assigned, in progress, or completed.
     *
     * @param bountyId    Bounty ID to cancel
     * @param currentUser Authenticated security principal attempting cancellation
     * @return Confirmation payload
     */
    @Transactional
    public BountyCancelResponse cancelBounty(Long bountyId, UserPrincipal currentUser) {
        log.info("Cancellation requested for bounty ID: {} by user ID: {}", bountyId, currentUser.getId());

        Bounty bounty = bountyRepository.findByIdForUpdate(bountyId)
                .or(() -> bountyRepository.findById(bountyId))
                .orElseThrow(() -> new ResourceNotFoundException("Bounty", "id", bountyId));

        // Ownership verification: Only the creating client or an admin may cancel
        boolean isOwner = bounty.getClient().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getRole() == Role.ROLE_ADMIN;

        if (!isOwner && !isAdmin) {
            log.warn("Unauthorized cancellation attempt: User ID {} is not owner of bounty ID {}",
                    currentUser.getId(), bountyId);
            throw new AccessDeniedException("You are not authorized to cancel this bounty. Only the bounty creator can cancel it.");
        }

        // State machine guard: read committed status to defeat cache during concurrent accept/cancel races
        BountyStatus currentStatus = bountyRepository.findStatusById(bountyId);
        if (currentStatus == null) {
            currentStatus = bounty.getStatus();
        }

        if (currentStatus == BountyStatus.CANCELLED) {
            throw new InvalidStateTransitionException("Bounty is already cancelled.");
        }

        if (currentStatus != BountyStatus.OPEN && currentStatus != BountyStatus.IN_REVIEW) {
            log.warn("Invalid cancellation attempt: Bounty ID {} is in state '{}'", bountyId, currentStatus);
            throw new InvalidStateTransitionException("Bounty", currentStatus, BountyStatus.CANCELLED);
        }

        bounty.setStatus(BountyStatus.CANCELLED);
        bountyRepository.saveAndFlush(bounty);

        // Reject all active PENDING proposals for this bounty to eliminate ghost proposals
        int rejectedProposals = proposalRepository.updateProposalsStatusByBountyIdAndStatus(
                bounty.getId(),
                ProposalStatus.PENDING,
                ProposalStatus.REJECTED
        );
        log.info("Transitioned {} pending proposals to REJECTED for cancelled bounty ID: {}", rejectedProposals, bounty.getId());

        log.info("Bounty ID: {} successfully cancelled by user ID: {}", bountyId, currentUser.getId());

        return BountyCancelResponse.builder()
                .id(bounty.getId())
                .status(BountyStatus.CANCELLED)
                .message("Bounty has been successfully cancelled.")
                .build();
    }

    private void validateNoMaliciousContent(String input, String fieldName) {
        if (input == null) return;
        String lower = input.toLowerCase();
        if (lower.contains("<script") || lower.contains("javascript:") || lower.contains("onerror=") || lower.contains("onload=")) {
            log.warn("Malicious script payload detected in bounty field '{}': {}", fieldName, input);
            throw new BadRequestException("Malicious content detected in " + fieldName + ". HTML script tags and javascript handlers are not permitted.");
        }
    }
}
