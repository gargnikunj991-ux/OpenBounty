package com.openbounty.repository;

import com.openbounty.model.LedgerTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {

    Optional<LedgerTransaction> findByTransactionReference(String transactionReference);

    @EntityGraph(attributePaths = {"entries"})
    @Query("SELECT t FROM LedgerTransaction t WHERE t.transactionReference = :ref")
    Optional<LedgerTransaction> findWithEntriesByTransactionReference(@Param("ref") String ref);

    @EntityGraph(attributePaths = {"entries"})
    @Query("SELECT t FROM LedgerTransaction t WHERE t.id = :id")
    Optional<LedgerTransaction> findWithEntriesById(@Param("id") Long id);

    List<LedgerTransaction> findByBountyIdOrderByCreatedAtDesc(Long bountyId);

    List<LedgerTransaction> findByMilestoneIdOrderByCreatedAtDesc(Long milestoneId);

    Page<LedgerTransaction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<LedgerTransaction> findAllByOrderByCreatedAtDesc(Pageable pageable);

    boolean existsByTransactionReference(String transactionReference);
}
