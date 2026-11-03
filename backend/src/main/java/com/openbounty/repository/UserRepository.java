package com.openbounty.repository;

import com.openbounty.enums.Role;
import com.openbounty.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by their unique email address.
     * Backed by unique B-Tree index 'idx_users_email' (O(log N) sub-millisecond lookup).
     */
    Optional<User> findByEmail(String email);

    /**
     * Highly optimized existence check.
     * Generates 'SELECT 1 FROM users WHERE email = ? LIMIT 1' without allocating entity objects in RAM.
     */
    boolean existsByEmail(String email);

    /**
     * Count users by their role for platform statistics and analytics overview.
     * Backed by index 'idx_users_role'.
     */
    long countByRole(Role role);

    /**
     * Acquires a PESSIMISTIC_WRITE (SELECT ... FOR UPDATE) lock on the user row.
     * Serializes concurrent reputation calculations, balance mutations, and state transitions,
     * preventing lost update race conditions across competing transactions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * Atomically increments or decrements a user's reputation score directly at the database level,
     * enforcing a floor of 0 so reputation never drops into negative numbers.
     */
    @Modifying
    @Query("UPDATE User u SET u.reputationScore = CASE WHEN (u.reputationScore + :delta) < 0 THEN 0 ELSE (u.reputationScore + :delta) END WHERE u.id = :id")
    int updateReputationScoreAtomically(@Param("id") Long id, @Param("delta") int delta);
}
