package com.openbounty.repository;

import com.openbounty.enums.DisputeStatus;
import com.openbounty.model.MilestoneDispute;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for MilestoneDispute persistence and pessimistic locking.
 */
@Repository
public interface MilestoneDisputeRepository extends JpaRepository<MilestoneDispute, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM MilestoneDispute d WHERE d.id = :id")
    Optional<MilestoneDispute> findByIdForUpdate(@Param("id") Long id);

    List<MilestoneDispute> findByMilestoneId(Long milestoneId);

    boolean existsByMilestoneIdAndStatus(Long milestoneId, DisputeStatus status);

    Page<MilestoneDispute> findByStatus(DisputeStatus status, Pageable pageable);

    @Query("SELECT d FROM MilestoneDispute d WHERE d.bounty.client.id = :userId OR d.bounty.assignedDeveloper.id = :userId")
    Page<MilestoneDispute> findByParticipantUserId(@Param("userId") Long userId, Pageable pageable);
}
