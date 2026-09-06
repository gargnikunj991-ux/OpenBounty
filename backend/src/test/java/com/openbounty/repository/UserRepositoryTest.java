package com.openbounty.repository;

import com.openbounty.enums.Role;
import com.openbounty.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User clientUser;
    private User devUser;

    @BeforeEach
    void setUp() {
        clientUser = User.builder()
                .name("Acme Corp")
                .email("contact@acme.org")
                .password("hashed_pwd_123")
                .role(Role.ROLE_CLIENT)
                .reputationScore(50)
                .build();

        devUser = User.builder()
                .name("Alice Solver")
                .email("alice@openbounty.dev")
                .password("hashed_pwd_456")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(100)
                .build();

        entityManager.persist(clientUser);
        entityManager.persist(devUser);
        entityManager.flush();
    }

    @Test
    @DisplayName("findByEmail returns User when email exists")
    void testFindByEmail_Success() {
        Optional<User> found = userRepository.findByEmail("contact@acme.org");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Acme Corp");
        assertThat(found.get().getRole()).isEqualTo(Role.ROLE_CLIENT);
    }

    @Test
    @DisplayName("findByEmail returns empty Optional when email does not exist")
    void testFindByEmail_NotFound() {
        Optional<User> found = userRepository.findByEmail("nonexistent@acme.org");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("existsByEmail returns true for existing email and false otherwise")
    void testExistsByEmail() {
        assertThat(userRepository.existsByEmail("contact@acme.org")).isTrue();
        assertThat(userRepository.existsByEmail("nobody@domain.com")).isFalse();
    }

    @Test
    @DisplayName("countByRole returns correct count for each role")
    void testCountByRole() {
        assertThat(userRepository.countByRole(Role.ROLE_CLIENT)).isEqualTo(1L);
        assertThat(userRepository.countByRole(Role.ROLE_DEVELOPER)).isEqualTo(1L);
        assertThat(userRepository.countByRole(Role.ROLE_ADMIN)).isEqualTo(0L);
    }

    @Test
    @DisplayName("saving user with duplicate email violates unique constraint")
    void testSave_DuplicateEmail_ThrowsException() {
        User duplicateUser = User.builder()
                .name("Duplicate Corp")
                .email("contact@acme.org")
                .password("another_pwd")
                .role(Role.ROLE_CLIENT)
                .build();

        assertThatThrownBy(() -> {
            entityManager.persist(duplicateUser);
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("saving user with null email violates non-null constraint")
    void testSave_NullEmail_ThrowsException() {
        User invalidUser = User.builder()
                .name("No Email")
                .email(null)
                .password("some_pwd")
                .role(Role.ROLE_CLIENT)
                .build();

        assertThatThrownBy(() -> {
            entityManager.persist(invalidUser);
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }
}
