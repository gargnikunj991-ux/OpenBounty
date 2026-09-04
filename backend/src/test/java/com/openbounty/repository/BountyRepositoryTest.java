package com.openbounty.repository;

import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.Role;
import com.openbounty.model.Bounty;
import com.openbounty.model.User;
import com.openbounty.repository.projection.CategoryStatsProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class BountyRepositoryTest {

    @Autowired
    private BountyRepository bountyRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User client;
    private User developer;
    private Bounty bountyOpen;
    private Bounty bountyCompleted;

    @BeforeEach
    void setUp() {
        client = User.builder()
                .name("Fintech Org")
                .email("fintech@openbounty.dev")
                .password("secure_hash")
                .role(Role.ROLE_CLIENT)
                .reputationScore(80)
                .build();
        entityManager.persist(client);

        developer = User.builder()
                .name("Bob Engineer")
                .email("bob@openbounty.dev")
                .password("secure_hash")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(120)
                .build();
        entityManager.persist(developer);

        bountyOpen = Bounty.builder()
                .title("Build Spring Security 6 JWT Module")
                .description("Need robust JWT authentication with filter chain")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(14))
                .client(client)
                .build();
        entityManager.persist(bountyOpen);

        bountyCompleted = Bounty.builder()
                .title("Develop Smart Contract Indexer")
                .description("Index blockchain transactions into PostgreSQL")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("2500.00"))
                .status(BountyStatus.COMPLETED)
                .deadline(LocalDate.now().minusDays(2))
                .client(client)
                .assignedDeveloper(developer)
                .build();
        entityManager.persist(bountyCompleted);

        entityManager.flush();
    }

    @Test
    @DisplayName("findWithDetailsById eagerly loads client and assignedDeveloper")
    void testFindWithDetailsById() {
        Optional<Bounty> found = bountyRepository.findWithDetailsById(bountyCompleted.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getClient().getName()).isEqualTo("Fintech Org");
        assertThat(found.get().getAssignedDeveloper()).isNotNull();
        assertThat(found.get().getAssignedDeveloper().getName()).isEqualTo("Bob Engineer");
    }

    @Test
    @DisplayName("findByStatus returns matching paginated bounties")
    void testFindByStatus() {
        Page<Bounty> page = bountyRepository.findByStatus(BountyStatus.OPEN, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1L);
        assertThat(page.getContent().get(0).getTitle()).contains("Spring Security");
    }

    @Test
    @DisplayName("findByCategory returns matching bounties")
    void testFindByCategory() {
        Page<Bounty> page = bountyRepository.findByCategory(BountyCategory.BACKEND_API, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findByStatusAndCategory filters by both criteria")
    void testFindByStatusAndCategory() {
        Page<Bounty> page = bountyRepository.findByStatusAndCategory(
                BountyStatus.COMPLETED, BountyCategory.BACKEND_API, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1L);
        assertThat(page.getContent().get(0).getTitle()).contains("Smart Contract");
    }

    @Test
    @DisplayName("searchBounties matches keyword in title or description case-insensitively")
    void testSearchBounties() {
        Page<Bounty> results = bountyRepository.searchBounties(
                BountyStatus.OPEN, BountyCategory.BACKEND_API, "security", PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(1L);
        assertThat(results.getContent().get(0).getTitle()).contains("Spring Security");
    }

    @Test
    @DisplayName("findByClientId returns bounties created by specific client")
    void testFindByClientId() {
        Page<Bounty> page = bountyRepository.findByClientId(client.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findByAssignedDeveloperId returns bounties assigned to developer")
    void testFindByAssignedDeveloperId() {
        Page<Bounty> page = bountyRepository.findByAssignedDeveloperId(developer.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1L);
        assertThat(page.getContent().get(0).getTitle()).contains("Smart Contract");
    }

    @Test
    @DisplayName("countByStatus returns correct count for each status")
    void testCountByStatus() {
        assertThat(bountyRepository.countByStatus(BountyStatus.OPEN)).isEqualTo(1L);
        assertThat(bountyRepository.countByStatus(BountyStatus.COMPLETED)).isEqualTo(1L);
        assertThat(bountyRepository.countByStatus(BountyStatus.CANCELLED)).isEqualTo(0L);
    }

    @Test
    @DisplayName("sumTotalCompletedRewardAmount calculates total reward for completed bounties")
    void testSumTotalCompletedRewardAmount() {
        BigDecimal total = bountyRepository.sumTotalCompletedRewardAmount();

        assertThat(total).isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("getCategoryStatistics groups and aggregates category statistics via projection")
    void testGetCategoryStatistics() {
        List<CategoryStatsProjection> stats = bountyRepository.getCategoryStatistics();

        assertThat(stats).isNotEmpty();
        CategoryStatsProjection backendStat = stats.stream()
                .filter(s -> s.getCategory() == BountyCategory.BACKEND_API)
                .findFirst()
                .orElse(null);

        assertThat(backendStat).isNotNull();
        assertThat(backendStat.getBountyCount()).isEqualTo(2L);
        assertThat(backendStat.getTotalRewardAmount()).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("findWithDetailsById returns empty Optional when ID does not exist")
    void testFindWithDetailsById_NotFound() {
        Optional<Bounty> found = bountyRepository.findWithDetailsById(999999L);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("searchBounties with all null filters returns all bounties")
    void testSearchBounties_AllNullFilters() {
        Page<Bounty> results = bountyRepository.searchBounties(
                null, null, null, PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(2L);
    }

    @Test
    @DisplayName("searchBounties returns empty page when keyword does not match any bounty")
    void testSearchBounties_NoMatch() {
        Page<Bounty> results = bountyRepository.searchBounties(
                null, null, "xyznonexistentkeyword", PageRequest.of(0, 10));

        assertThat(results.getTotalElements()).isEqualTo(0L);
        assertThat(results.getContent()).isEmpty();
    }

    @Test
    @DisplayName("sumTotalCompletedRewardAmount returns zero when no completed bounties exist")
    void testSumTotalCompletedRewardAmount_ZeroWhenNoneCompleted() {
        entityManager.remove(bountyCompleted);
        entityManager.flush();

        BigDecimal total = bountyRepository.sumTotalCompletedRewardAmount();
        assertThat(total).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("saving bounty with null client violates non-null constraint")
    void testSave_NullClient_ThrowsException() {
        Bounty invalidBounty = Bounty.builder()
                .title("Invalid Bounty Without Client")
                .description("No client attached")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("100.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(5))
                .client(null)
                .build();

        assertThatThrownBy(() -> {
            entityManager.persist(invalidBounty);
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }
}
