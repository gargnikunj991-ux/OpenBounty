package com.openbounty.repository;

import com.openbounty.enums.MilestoneStatus;
import com.openbounty.model.Milestone;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    /**
     * Fetch milestones for a given proposal in ascending chronological/sequence order.
     * Backed by index 'idx_milestones_proposal_id'.
     */
    List<Milestone> findByProposalIdOrderByIdAsc(Long proposalId);

    /**
     * Retrieve a milestone by ID with proposal, bounty, and developer relationships eagerly loaded.
     * Prevents multiple SELECT queries when verifying deliverable submission ownership or client approvals.
     */
    @EntityGraph(attributePaths = {"proposal", "proposal.bounty", "proposal.developer"})
    @Query("SELECT m FROM Milestone m WHERE m.id = :id")
    Optional<Milestone> findWithDetailsById(@Param("id") Long id);

    /**
     * Count total milestones belonging to a proposal.
     */
    long countByProposalId(Long proposalId);

    /**
     * Count milestones belonging to a proposal that match a specific status (e.g. APPROVED).
     * Backed by composite lookup using 'idx_milestones_proposal_id' and 'idx_milestones_status'.
     */
    long countByProposalIdAndStatus(Long proposalId, MilestoneStatus status);

    /**
     * Check if any milestone in a proposal is not yet approved.
     * Used for fast auto-completion verification without iterating through all entities in memory.
     */
    boolean existsByProposalIdAndStatusNot(Long proposalId, MilestoneStatus status);
}
