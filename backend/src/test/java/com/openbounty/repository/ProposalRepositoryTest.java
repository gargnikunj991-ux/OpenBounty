package com.openbounty.repository;

import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ProposalRepositoryTest {

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User client;
    private User dev1;
    private User dev2;
    private Bounty bounty;
    private Proposal proposal1;
    private Proposal proposal2;

    @BeforeEach
    void setUp() {
        client = User.builder()
                .name("Alpha Corp")
                .email("alpha@corp.com")
                .password("hash")
                .role(Role.ROLE_CLIENT)
                .build();
        entityManager.persist(client);

        dev1 = User.builder()
                .name("Dev One")
                .email("dev1@solvers.com")
                .password("hash")
                .role(Role.ROLE_DEVELOPER)
                .build();
        entityManager.persist(dev1);

        dev2 = User.builder()
                .name("Dev Two")
                .email("dev2@solvers.com")
                .password("hash")
                .role(Role.ROLE_DEVELOPER)
                .build();
        entityManager.persist(dev2);

        bounty = Bounty.builder()
                .title("GraphQL API Gateway")
                .description("Build federated GraphQL gateway")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("3000.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(20))
                .client(client)
                .build();
        entityManager.persist(bounty);

        proposal1 = Proposal.builder()
                .bounty(bounty)
                .developer(dev1)
                .approachDescription("Implement with Apollo Federation")
                .proposedAmount(new BigDecimal("2800.00"))
                .estimatedDays(10)
                .status(ProposalStatus.PENDING)
                .build();
        proposal1.addMilestone(Milestone.builder()
                .title("Gateway Routing")
                .description("Route subgraphs")
                .build());
        entityManager.persist(proposal1);

        proposal2 = Proposal.builder()
                .bounty(bounty)
                .developer(dev2)
                .approachDescription("Implement with Netflix DGS")
                .proposedAmount(new BigDecimal("2900.00"))
                .estimatedDays(12)
                .status(ProposalStatus.PENDING)
                .build();
        entityManager.persist(proposal2);

        entityManager.flush();
    }

    @Test
    @DisplayName("findWithDetailsById eagerly loads bounty, developer, and milestones")
    void testFindWithDetailsById() {
        Optional<Proposal> found = proposalRepository.findWithDetailsById(proposal1.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getBounty().getTitle()).isEqualTo("GraphQL API Gateway");
        assertThat(found.get().getDeveloper().getName()).isEqualTo("Dev One");
        assertThat(found.get().getMilestones()).hasSize(1);
    }

    @Test
    @DisplayName("existsByBountyIdAndDeveloperId returns true when developer already submitted")
    void testExistsByBountyIdAndDeveloperId() {
        assertThat(proposalRepository.existsByBountyIdAndDeveloperId(bounty.getId(), dev1.getId())).isTrue();
        assertThat(proposalRepository.existsByBountyIdAndDeveloperId(bounty.getId(), client.getId())).isFalse();
    }

    @Test
    @DisplayName("findByBountyId returns paginated proposals for bounty")
    void testFindByBountyId_Paginated() {
        Page<Proposal> page = proposalRepository.findByBountyId(bounty.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findByDeveloperId returns proposals by specific developer")
    void testFindByDeveloperId() {
        Page<Proposal> page = proposalRepository.findByDeveloperId(dev1.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1L);
        assertThat(page.getContent().get(0).getDeveloper().getName()).isEqualTo("Dev One");
    }

    @Test
    @DisplayName("findByBountyIdAndStatus filters proposals by lifecycle status")
    void testFindByBountyIdAndStatus() {
        List<Proposal> pendings = proposalRepository.findByBountyIdAndStatus(bounty.getId(), ProposalStatus.PENDING);

        assertThat(pendings).hasSize(2);
    }

    @Test
    @DisplayName("countByBountyId returns total proposal count for bounty")
    void testCountByBountyId() {
        assertThat(proposalRepository.countByBountyId(bounty.getId())).isEqualTo(2L);
    }

    @Test
    @DisplayName("rejectCompetingProposals updates all competing proposals to REJECTED")
    void testRejectCompetingProposals() {
        int updatedCount = proposalRepository.rejectCompetingProposals(
                bounty.getId(), proposal1.getId(), ProposalStatus.REJECTED);

        assertThat(updatedCount).isEqualTo(1);

        entityManager.clear();

        Proposal reloadedProp2 = entityManager.find(Proposal.class, proposal2.getId());
        assertThat(reloadedProp2.getStatus()).isEqualTo(ProposalStatus.REJECTED);

        Proposal reloadedProp1 = entityManager.find(Proposal.class, proposal1.getId());
        assertThat(reloadedProp1.getStatus()).isEqualTo(ProposalStatus.PENDING);
    }

    @Test
    @DisplayName("findWithDetailsById returns empty Optional when ID does not exist")
    void testFindWithDetailsById_NotFound() {
        Optional<Proposal> found = proposalRepository.findWithDetailsById(999999L);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByDeveloperId returns empty page when developer has no proposals")
    void testFindByDeveloperId_NoProposals() {
        Page<Proposal> page = proposalRepository.findByDeveloperId(client.getId(), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(0L);
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("saving duplicate proposal for same bounty and developer violates unique constraint")
    void testSave_DuplicateProposal_ThrowsException() {
        Proposal duplicateProposal = Proposal.builder()
                .bounty(bounty)
                .developer(dev1) // dev1 already submitted proposal1
                .approachDescription("Duplicate approach")
                .proposedAmount(new BigDecimal("2500.00"))
                .estimatedDays(5)
                .status(ProposalStatus.PENDING)
                .build();

        assertThatThrownBy(() -> {
            entityManager.persist(duplicateProposal);
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }
}
