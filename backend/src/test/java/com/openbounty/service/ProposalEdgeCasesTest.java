package com.openbounty.service;

import com.openbounty.dto.request.milestone.MilestoneCreateRequest;
import com.openbounty.dto.request.proposal.ProposalCreateRequest;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.exception.AccessDeniedException;
import com.openbounty.exception.BadRequestException;
import com.openbounty.exception.BountyExpiredException;
import com.openbounty.exception.DuplicateResourceException;
import com.openbounty.exception.InvalidStateTransitionException;
import com.openbounty.exception.SelfBiddingNotAllowedException;
import com.openbounty.model.Bounty;
import com.openbounty.model.Proposal;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.ProposalRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

/**
 * Isolated unit tests examining the 5 core adversarial edge cases in Proposal lifecycle:
 * 
 * 1. Stale / Expired Bounty Bidding (Deadline check)
 * 2. Self-Dealing / Sybil Bidding (SelfBiddingNotAllowedException)
 * 3. Budget Runaway / Exorbitant Proposal Amount (proposedAmount > rewardAmount)
 * 4. Boundary at exact deadline date
 * 5. Cross-Tenant IDOR on Rejection / Acceptance
 */
@ExtendWith(MockitoExtension.class)
class ProposalEdgeCasesTest {

    @Mock
    private ProposalRepository proposalRepository;

    @Mock
    private BountyRepository bountyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProposalService proposalService;

    private User clientUser;
    private User devUser;
    private User attackerClient;
    private UserPrincipal clientPrincipal;
    private UserPrincipal devPrincipal;
    private UserPrincipal attackerPrincipal;
    private Bounty sampleBounty;

    @BeforeEach
    void setUp() {
        clientUser = User.builder()
                .id(1L)
                .name("Acme Corp")
                .email("client@acme.org")
                .role(Role.ROLE_CLIENT)
                .build();

        attackerClient = User.builder()
                .id(2L)
                .name("Attacker Corp")
                .email("attacker@evil.io")
                .role(Role.ROLE_CLIENT)
                .build();

        devUser = User.builder()
                .id(10L)
                .name("Alice Solver")
                .email("alice@solver.dev")
                .role(Role.ROLE_DEVELOPER)
                .build();

        clientPrincipal = UserPrincipal.create(clientUser);
        attackerPrincipal = UserPrincipal.create(attackerClient);
        devPrincipal = UserPrincipal.create(devUser);

        sampleBounty = Bounty.builder()
                .id(100L)
                .title("Build Payment Engine")
                .description("Stripe Integration")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1000.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .build();
    }

    @Nested
    @DisplayName("Edge Case 1: Deadline & Stale Bounty Bidding")
    class DeadlineEdgeCases {

        @Test
        @DisplayName("EDGE CASE: Submitting proposal to a bounty where deadline was yesterday must throw BountyExpiredException")
        void proposalOnExpiredBounty_ShouldThrowBountyExpiredException() {
            // Bounty deadline was yesterday
            sampleBounty.setDeadline(LocalDate.now().minusDays(1));

            ProposalCreateRequest request = ProposalCreateRequest.builder()
                    .approachDescription("Submitting to an expired challenge")
                    .proposedAmount(new BigDecimal("800.00"))
                    .estimatedDays(3)
                    .milestones(List.of(MilestoneCreateRequest.builder().title("M1").build()))
                    .build();

            when(userRepository.findById(devPrincipal.getId())).thenReturn(Optional.of(devUser));
            when(bountyRepository.findById(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));

            assertThatThrownBy(() -> proposalService.submitProposal(sampleBounty.getId(), request, devPrincipal))
                    .isInstanceOf(BountyExpiredException.class)
                    .hasMessageContaining("expired");

            verify(proposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("EDGE CASE: Submitting proposal on the exact day of deadline (today) should be allowed")
        void proposalOnDeadlineDay_ShouldBeAllowed() {
            sampleBounty.setDeadline(LocalDate.now()); // Today

            ProposalCreateRequest request = ProposalCreateRequest.builder()
                    .approachDescription("Submitting on the final day before expiration")
                    .proposedAmount(new BigDecimal("900.00"))
                    .estimatedDays(2)
                    .milestones(List.of(MilestoneCreateRequest.builder().title("M1").build()))
                    .build();

            when(userRepository.findById(devPrincipal.getId())).thenReturn(Optional.of(devUser));
            when(bountyRepository.findById(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));
            when(proposalRepository.existsByBountyIdAndDeveloperId(sampleBounty.getId(), devUser.getId())).thenReturn(false);
            when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> {
                Proposal p = inv.getArgument(0);
                p.setId(501L);
                return p;
            });

            // Should succeed without throwing BountyExpiredException
            assertThat(proposalService.submitProposal(sampleBounty.getId(), request, devPrincipal)).isNotNull();
        }
    }

    @Nested
    @DisplayName("Edge Case 2: Self-Dealing & Reputation Manipulation")
    class SelfDealingEdgeCases {

        @Test
        @DisplayName("EDGE CASE: Client attempting to bid on their own bounty must throw SelfBiddingNotAllowedException (not generic 403)")
        void clientBiddingOnOwnBounty_ShouldThrowSelfBiddingNotAllowedException() {
            // User has dual capabilities or developer role, but owns the target bounty
            User sneakyDev = User.builder()
                    .id(clientUser.getId()) // Same ID as client
                    .name("Sneaky Client")
                    .email("client@acme.org")
                    .role(Role.ROLE_DEVELOPER)
                    .build();
            UserPrincipal sneakyPrincipal = UserPrincipal.create(sneakyDev);

            ProposalCreateRequest request = ProposalCreateRequest.builder()
                    .approachDescription("Bidding on my own bounty")
                    .proposedAmount(new BigDecimal("1000.00"))
                    .estimatedDays(1)
                    .build();

            when(userRepository.findById(sneakyPrincipal.getId())).thenReturn(Optional.of(sneakyDev));
            when(bountyRepository.findById(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));

            assertThatThrownBy(() -> proposalService.submitProposal(sampleBounty.getId(), request, sneakyPrincipal))
                    .isInstanceOf(SelfBiddingNotAllowedException.class)
                    .hasMessageContaining("own bounty");

            verify(proposalRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Edge Case 3: Financial Budget Limit")
    class BudgetEdgeCases {

        @Test
        @DisplayName("EDGE CASE: Proposal amount exceeding bounty reward must throw BadRequestException")
        void proposalExceedingBountyReward_ShouldThrowBadRequestException() {
            // Bounty reward is $1,000. Developer bids $1,000.01 (over budget)
            ProposalCreateRequest request = ProposalCreateRequest.builder()
                    .approachDescription("Asking for more money than client budgeted")
                    .proposedAmount(new BigDecimal("1000.01"))
                    .estimatedDays(5)
                    .build();

            when(userRepository.findById(devPrincipal.getId())).thenReturn(Optional.of(devUser));
            when(bountyRepository.findById(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));

            assertThatThrownBy(() -> proposalService.submitProposal(sampleBounty.getId(), request, devPrincipal))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("cannot exceed bounty reward");

            verify(proposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("EDGE CASE: Proposal amount exactly equal to bounty reward ($1,000.00) should be allowed")
        void proposalEqualBountyReward_ShouldBeAllowed() {
            ProposalCreateRequest request = ProposalCreateRequest.builder()
                    .approachDescription("Asking for exact posted reward")
                    .proposedAmount(new BigDecimal("1000.00"))
                    .estimatedDays(5)
                    .milestones(List.of(MilestoneCreateRequest.builder().title("M1").build()))
                    .build();

            when(userRepository.findById(devPrincipal.getId())).thenReturn(Optional.of(devUser));
            when(bountyRepository.findById(sampleBounty.getId())).thenReturn(Optional.of(sampleBounty));
            when(proposalRepository.existsByBountyIdAndDeveloperId(sampleBounty.getId(), devUser.getId())).thenReturn(false);
            when(proposalRepository.save(any(Proposal.class))).thenAnswer(inv -> {
                Proposal p = inv.getArgument(0);
                p.setId(502L);
                return p;
            });

            assertThat(proposalService.submitProposal(sampleBounty.getId(), request, devPrincipal)).isNotNull();
        }
    }

    @Nested
    @DisplayName("Edge Case 4: Cross-Tenant IDOR and Unauthorized State Mutations")
    class IdorEdgeCases {

        @Test
        @DisplayName("EDGE CASE: Malicious client attempting to reject another client's proposal must be rejected")
        void maliciousClientRejectingCompetitorProposal_ShouldThrowAccessDeniedException() {
            Proposal targetProposal = Proposal.builder()
                    .id(200L)
                    .bounty(sampleBounty) // Owned by clientUser (ID: 1)
                    .developer(devUser)
                    .status(ProposalStatus.PENDING)
                    .build();

            when(proposalRepository.findWithDetailsById(targetProposal.getId())).thenReturn(Optional.of(targetProposal));

            // Attacker (ID: 2) calls reject
            assertThatThrownBy(() -> proposalService.rejectProposal(targetProposal.getId(), attackerPrincipal))
                    .isInstanceOf(AccessDeniedException.class);

            verify(proposalRepository, never()).save(any());
        }

        @Test
        @DisplayName("EDGE CASE: Rejecting an already accepted or rejected proposal must be forbidden")
        void rejectingAlreadyDecidedProposal_ShouldThrowInvalidStateTransitionException() {
            Proposal decidedProposal = Proposal.builder()
                    .id(201L)
                    .bounty(sampleBounty)
                    .developer(devUser)
                    .status(ProposalStatus.ACCEPTED) // Already accepted
                    .build();

            when(proposalRepository.findWithDetailsById(decidedProposal.getId())).thenReturn(Optional.of(decidedProposal));

            assertThatThrownBy(() -> proposalService.rejectProposal(decidedProposal.getId(), clientPrincipal))
                    .isInstanceOf(InvalidStateTransitionException.class);

            verify(proposalRepository, never()).save(any());
        }
    }
}
