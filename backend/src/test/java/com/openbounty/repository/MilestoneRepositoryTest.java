package com.openbounty.repository;

import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.model.Bounty;
import com.openbounty.model.Milestone;
import com.openbounty.model.Proposal;
import com.openbounty.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MilestoneRepositoryTest {

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Proposal proposal;
    private Milestone m1;
    private Milestone m2;

    @BeforeEach
    void setUp() {
        User client = User.builder()
                .name("Beta Labs")
                .email("beta@labs.io")
                .password("hash")
                .role(Role.ROLE_CLIENT)
                .build();
        entityManager.persist(client);

        User dev = User.builder()
                .name("Dev Charlie")
                .email("charlie@openbounty.dev")
                .password("hash")
                .role(Role.ROLE_DEVELOPER)
                .build();
        entityManager.persist(dev);

        Bounty bounty = Bounty.builder()
                .title("Solana Anchor Escrow Program")
                .description("Smart contract escrow in Rust")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("4000.00"))
                .status(BountyStatus.ASSIGNED)
                .deadline(LocalDate.now().plusDays(30))
                .client(client)
                .assignedDeveloper(dev)
                .build();
        entityManager.persist(bounty);

        proposal = Proposal.builder()
                .bounty(bounty)
                .developer(dev)
                .approachDescription("Rust Anchor Program with client tests")
                .proposedAmount(new BigDecimal("3800.00"))
                .estimatedDays(14)
                .status(ProposalStatus.ACCEPTED)
                .build();
        entityManager.persist(proposal);

        m1 = Milestone.builder()
                .proposal(proposal)
                .title("Milestone 1: Escrow Initialization")
                .description("Initialize escrow PDA with token account")
                .status(MilestoneStatus.APPROVED)
                .approvedAt(LocalDateTime.now())
                .build();
        entityManager.persist(m1);

        m2 = Milestone.builder()
                .proposal(proposal)
                .title("Milestone 2: Escrow Exchange & Settlement")
                .description("Atomic token exchange logic and client tests")
                .status(MilestoneStatus.SUBMITTED)
                .deliverableUrl("https://github.com/charlie/escrow/pull/1")
                .submittedAt(LocalDateTime.now())
                .build();
        entityManager.persist(m2);

        entityManager.flush();
    }

    @Test
    @DisplayName("findByProposalIdOrderByIdAsc returns milestones in chronological/sequence order")
    void testFindByProposalIdOrderByIdAsc() {
        List<Milestone> milestones = milestoneRepository.findByProposalIdOrderByIdAsc(proposal.getId());

        assertThat(milestones).hasSize(2);
        assertThat(milestones.get(0).getTitle()).contains("Milestone 1");
        assertThat(milestones.get(1).getTitle()).contains("Milestone 2");
    }

    @Test
    @DisplayName("findWithDetailsById eagerly loads proposal, bounty, and developer")
    void testFindWithDetailsById() {
        Optional<Milestone> found = milestoneRepository.findWithDetailsById(m2.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getProposal().getBounty().getTitle()).contains("Solana Anchor");
        assertThat(found.get().getProposal().getDeveloper().getName()).isEqualTo("Dev Charlie");
    }

    @Test
    @DisplayName("countByProposalId returns total milestone count")
    void testCountByProposalId() {
        assertThat(milestoneRepository.countByProposalId(proposal.getId())).isEqualTo(2L);
    }

    @Test
    @DisplayName("countByProposalIdAndStatus counts milestones matching specific status")
    void testCountByProposalIdAndStatus() {
        assertThat(milestoneRepository.countByProposalIdAndStatus(proposal.getId(), MilestoneStatus.APPROVED)).isEqualTo(1L);
        assertThat(milestoneRepository.countByProposalIdAndStatus(proposal.getId(), MilestoneStatus.SUBMITTED)).isEqualTo(1L);
        assertThat(milestoneRepository.countByProposalIdAndStatus(proposal.getId(), MilestoneStatus.PENDING)).isEqualTo(0L);
    }

    @Test
    @DisplayName("existsByProposalIdAndStatusNot checks if any milestone is not approved")
    void testExistsByProposalIdAndStatusNot() {
        // m2 is SUBMITTED, so there exists at least one milestone with status != APPROVED
        boolean hasUnapproved = milestoneRepository.existsByProposalIdAndStatusNot(proposal.getId(), MilestoneStatus.APPROVED);
        assertThat(hasUnapproved).isTrue();

        // Update m2 to APPROVED
        m2.setStatus(MilestoneStatus.APPROVED);
        entityManager.persist(m2);
        entityManager.flush();

        // Now all milestones are APPROVED, so status != APPROVED should be false
        boolean hasUnapprovedAfterAllApproved = milestoneRepository.existsByProposalIdAndStatusNot(proposal.getId(), MilestoneStatus.APPROVED);
        assertThat(hasUnapprovedAfterAllApproved).isFalse();
    }

    @Test
    @DisplayName("findWithDetailsById returns empty Optional when milestone ID does not exist")
    void testFindWithDetailsById_NotFound() {
        Optional<Milestone> found = milestoneRepository.findWithDetailsById(999999L);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByProposalIdOrderByIdAsc returns empty list when proposal has no milestones")
    void testFindByProposalIdOrderByIdAsc_Empty() {
        List<Milestone> milestones = milestoneRepository.findByProposalIdOrderByIdAsc(999999L);
        assertThat(milestones).isEmpty();
    }
}
