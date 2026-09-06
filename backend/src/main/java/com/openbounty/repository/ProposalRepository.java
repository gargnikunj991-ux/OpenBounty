package com.openbounty.repository;

import com.openbounty.enums.ProposalStatus;
import com.openbounty.model.Proposal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    /**
     * Fetch a proposal by ID with its bounty, developer, and milestones eagerly loaded.
     * Prevents N+1 SELECT queries when retrieving proposal details or evaluating acceptance.
     */
    @EntityGraph(attributePaths = {"bounty", "developer", "milestones"})
    @Query("SELECT p FROM Proposal p WHERE p.id = :id")
    Optional<Proposal> findWithDetailsById(@Param("id") Long id);

    /**
     * Check if a developer has already submitted a proposal for a given bounty.
     * Backed by unique constraint 'uq_bounty_developer' (bounty_id, developer_id).
     * Used as an application guard to reject duplicate proposal submissions early.
     */
    boolean existsByBountyIdAndDeveloperId(Long bountyId, Long developerId);

    /**
     * Paginated list of proposals submitted for a specific bounty.
     * Eagerly fetches developer profile to display bid author information without N+1 queries.
     * Backed by index 'idx_proposals_bounty_id'.
     */
    @EntityGraph(attributePaths = {"developer"})
    Page<Proposal> findByBountyId(Long bountyId, Pageable pageable);

    /**
     * Retrieve all proposals submitted for a bounty (unpaginated).
     * Backed by index 'idx_proposals_bounty_id'.
     */
    @EntityGraph(attributePaths = {"developer"})
    List<Proposal> findByBountyId(Long bountyId);

    /**
     * Paginated list of proposals submitted by a specific developer.
     * Eagerly fetches bounty details for developer dashboard views.
     * Backed by index 'idx_proposals_developer_id'.
     */
    @EntityGraph(attributePaths = {"bounty"})
    Page<Proposal> findByDeveloperId(Long developerId, Pageable pageable);

    /**
     * Find proposals for a bounty matching a specific status (e.g., PENDING).
     * Backed by composite lookup on bounty_id and status.
     */
    List<Proposal> findByBountyIdAndStatus(Long bountyId, ProposalStatus status);

    /**
     * Count total proposals submitted for a bounty.
     */
    long countByBountyId(Long bountyId);

    /**
     * Atomic JPQL bulk update to reject all other competing proposals when one is accepted.
     * Executes in a single SQL statement:
     * UPDATE proposals SET status = 'REJECTED' WHERE bounty_id = ? AND id <> ?
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Proposal p SET p.status = :rejectedStatus WHERE p.bounty.id = :bountyId AND p.id <> :acceptedProposalId")
    int rejectCompetingProposals(
            @Param("bountyId") Long bountyId,
            @Param("acceptedProposalId") Long acceptedProposalId,
            @Param("rejectedStatus") ProposalStatus rejectedStatus
    );
}
