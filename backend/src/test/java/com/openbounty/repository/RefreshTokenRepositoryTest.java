package com.openbounty.repository;

import com.openbounty.enums.Role;
import com.openbounty.model.RefreshToken;
import com.openbounty.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User sampleUser;
    private RefreshToken activeToken;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .name("Alex Johnson")
                .email("alex@example.com")
                .password("hash123")
                .role(Role.ROLE_DEVELOPER)
                .build();
        entityManager.persist(sampleUser);

        activeToken = RefreshToken.builder()
                .user(sampleUser)
                .token("test-unique-refresh-token")
                .expiryDate(Instant.now().plusSeconds(86400))
                .revoked(false)
                .build();
        entityManager.persist(activeToken);
        entityManager.flush();
    }

    @Test
    @DisplayName("findByToken returns token when exists")
    void testFindByToken_Found() {
        Optional<RefreshToken> found = refreshTokenRepository.findByToken("test-unique-refresh-token");

        assertThat(found).isPresent();
        assertThat(found.get().getUser().getEmail()).isEqualTo("alex@example.com");
        assertThat(found.get().isRevoked()).isFalse();
    }

    @Test
    @DisplayName("findByToken returns empty when token does not exist")
    void testFindByToken_NotFound() {
        Optional<RefreshToken> found = refreshTokenRepository.findByToken("non-existent");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("deleteByUser deletes all refresh tokens belonging to user")
    void testDeleteByUser() {
        int deleted = refreshTokenRepository.deleteByUser(sampleUser);

        assertThat(deleted).isEqualTo(1);
        assertThat(refreshTokenRepository.findByToken("test-unique-refresh-token")).isEmpty();
    }
}
