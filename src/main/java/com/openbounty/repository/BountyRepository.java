package com.openbounty.repository;

import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.model.Bounty;
import com.openbounty.repository.projection.CategoryStatsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BountyRepository extends JpaRepository<Bounty, Long>, JpaSpecificationExecutor<Bounty> {

    /**
     * Find a bounty by ID with client and assigned developer eagerly fetched.
     * Prevents N+1 queries when building detailed responses.
     */
    @EntityGraph(attributePaths = {"client", "assignedDeveloper"})
    @Query("SELECT b FROM Bounty b WHERE b.id = :id")
    Optional<Bounty> findWithDetailsById(@Param("id") Long id);

    /**
     * Paginated lookup of bounties filtered by lifecycle status.
     * Backed by index 'idx_bounties_status'.
     */
    Page<Bounty> findByStatus(BountyStatus status, Pageable pageable);

    /**
     * Paginated lookup of bounties filtered by category.
     * Backed by index 'idx_bounties_category'.
     */
    Page<Bounty> findByCategory(BountyCategory category, Pageable pageable);

    /**
     * Paginated lookup of bounties filtered by both status and category.
     * Backed by composite index 'idx_bounties_status_category'.
     */
    Page<Bounty> findByStatusAndCategory(BountyStatus status, BountyCategory category, Pageable pageable);

    /**
     * Flexible marketplace search query with optional filters for status, category, and keyword.
     * Keyword search performs case-insensitive matching across title and description.
     * Eagerly fetches 'client' to avoid N+1 select queries when rendering the marketplace list.
     */
    @EntityGraph(attributePaths = {"client"})
    @Query("""
        SELECT b FROM Bounty b
        WHERE (:status IS NULL OR b.status = :status)
          AND (:category IS NULL OR b.category = :category)
          AND (:keyword IS NULL OR LOWER(b.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                             OR LOWER(b.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    Page<Bounty> searchBounties(
            @Param("status") BountyStatus status,
            @Param("category") BountyCategory category,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /**
     * Retrieve all bounties created by a specific client.
     * Backed by index 'idx_bounties_client_id'.
     */
    Page<Bounty> findByClientId(Long clientId, Pageable pageable);

    /**
     * Retrieve all bounties assigned to a specific developer.
     * Backed by index 'idx_bounties_assigned_dev_id'.
     */
    Page<Bounty> findByAssignedDeveloperId(Long developerId, Pageable pageable);

    /**
     * Count bounties by their current status for platform statistics.
     */
    long countByStatus(BountyStatus status);

    /**
     * Calculate total funds disbursed across all completed bounties.
     * Returns BigDecimal.ZERO if no completed bounties exist.
     */
    @Query("SELECT COALESCE(SUM(b.rewardAmount), 0) FROM Bounty b WHERE b.status = com.openbounty.enums.BountyStatus.COMPLETED")
    BigDecimal sumTotalCompletedRewardAmount();

    /**
     * Aggregation query for category distribution breakdown.
     * Groups bounties by category with counts and sum of reward amounts.
     */
    @Query("""
        SELECT b.category AS category,
               COUNT(b) AS bountyCount,
               COALESCE(SUM(b.rewardAmount), 0) AS totalRewardAmount
        FROM Bounty b
        GROUP BY b.category
    """)
    List<CategoryStatsProjection> getCategoryStatistics();
}
