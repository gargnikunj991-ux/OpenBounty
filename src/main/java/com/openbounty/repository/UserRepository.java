package com.openbounty.repository;

import com.openbounty.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
