package com.openbounty.repository;

import com.openbounty.model.IdempotencyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Spring Data JPA repository for IdempotencyRecord persistence, lookups, and pessimistic locking.
 */
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IdempotencyRecord i WHERE i.idempotencyKey = :key")
    Optional<IdempotencyRecord> findByIdempotencyKeyForUpdate(@Param("key") String key);

    @Modifying
    @Query("DELETE FROM IdempotencyRecord i WHERE i.expiresAt < :now")
    int deleteExpiredKeys(@Param("now") LocalDateTime now);
}
