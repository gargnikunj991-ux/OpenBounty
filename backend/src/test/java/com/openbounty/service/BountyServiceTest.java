package com.openbounty.service;

import com.openbounty.dto.request.bounty.BountyCreateRequest;
import com.openbounty.dto.response.bounty.BountyCancelResponse;
import com.openbounty.dto.response.bounty.BountyResponse;
import com.openbounty.dto.response.bounty.BountySummaryResponse;
import com.openbounty.dto.response.common.PagedResponse;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.Role;
import com.openbounty.exception.AccessDeniedException;
import com.openbounty.exception.InvalidStateTransitionException;
import com.openbounty.exception.ResourceNotFoundException;
import com.openbounty.model.Bounty;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BountyServiceTest {

    @Mock
    private BountyRepository bountyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BountyService bountyService;

    private User clientUser;
    private User devUser;
    private UserPrincipal clientPrincipal;
    private UserPrincipal devPrincipal;
    private UserPrincipal adminPrincipal;
    private Bounty sampleBounty;

    @BeforeEach
    void setUp() {
        clientUser = User.builder()
                .id(10L)
                .name("Acme Client")
                .email("client@acme.org")
                .password("hash123")
                .role(Role.ROLE_CLIENT)
                .reputationScore(50)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        devUser = User.builder()
                .id(20L)
                .name("Dev Solver")
                .email("solver@openbounty.org")
                .password("hash123")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(100)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        clientPrincipal = UserPrincipal.create(clientUser);
        devPrincipal = UserPrincipal.create(devUser);
        adminPrincipal = UserPrincipal.fromClaims(99L, "Admin User", "admin@openbounty.org", Role.ROLE_ADMIN);

        sampleBounty = Bounty.builder()
                .id(101L)
                .title("Build Spring Security 6 JWT Module")
                .description("Detailed requirement description for building JWT authentication.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(30))
                .client(clientUser)
                .assignedDeveloper(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("createBounty creates OPEN bounty for client successfully")
    void testCreateBounty_Success() {
        BountyCreateRequest request = BountyCreateRequest.builder()
                .title("Build Spring Security 6 JWT Module")
                .description("Detailed requirement description for building JWT authentication.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .deadline(LocalDate.now().plusDays(30))
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(clientUser));
        when(bountyRepository.save(any(Bounty.class))).thenAnswer(invocation -> {
            Bounty b = invocation.getArgument(0);
            b.setId(101L);
            b.setCreatedAt(LocalDateTime.now());
            b.setUpdatedAt(LocalDateTime.now());
            return b;
        });

        BountyResponse response = bountyService.createBounty(request, clientPrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getTitle()).isEqualTo("Build Spring Security 6 JWT Module");
        assertThat(response.getStatus()).isEqualTo(BountyStatus.OPEN);
        assertThat(response.getClient().getId()).isEqualTo(10L);
        assertThat(response.getAssignedDeveloper()).isNull();

        verify(bountyRepository).save(any(Bounty.class));
    }

    @Test
    @DisplayName("createBounty throws ResourceNotFoundException when user is not found in database")
    void testCreateBounty_UserNotFound() {
        BountyCreateRequest request = BountyCreateRequest.builder()
                .title("Build Spring Security 6 JWT Module")
                .description("Detailed description.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .deadline(LocalDate.now().plusDays(30))
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bountyService.createBounty(request, clientPrincipal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: '10'");

        verify(bountyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createBounty throws AccessDeniedException when caller is not ROLE_CLIENT or ROLE_ADMIN")
    void testCreateBounty_DeveloperRole_ThrowsAccessDenied() {
        BountyCreateRequest request = BountyCreateRequest.builder()
                .title("Build Spring Security 6 JWT Module")
                .description("Detailed description.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .deadline(LocalDate.now().plusDays(30))
                .build();

        when(userRepository.findById(20L)).thenReturn(Optional.of(devUser));

        assertThatThrownBy(() -> bountyService.createBounty(request, devPrincipal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only clients can create technical bounties");

        verify(bountyRepository, never()).save(any());
    }

    @Test
    @DisplayName("getBounties returns paged summary results")
    void testGetBounties_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Bounty> page = new PageImpl<>(List.of(sampleBounty), pageable, 1);

        when(bountyRepository.searchBounties(eq(BountyStatus.OPEN), eq(BountyCategory.BACKEND_API), eq("JWT"), eq(pageable)))
                .thenReturn(page);

        PagedResponse<BountySummaryResponse> response = bountyService.getBounties(
                BountyStatus.OPEN, BountyCategory.BACKEND_API, "  JWT  ", pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(101L);
        assertThat(response.getContent().get(0).getTitle()).isEqualTo("Build Spring Security 6 JWT Module");
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getTotalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("getBountyById returns full details when found")
    void testGetBountyById_Success() {
        when(bountyRepository.findWithDetailsById(101L)).thenReturn(Optional.of(sampleBounty));

        BountyResponse response = bountyService.getBountyById(101L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getDescription()).contains("JWT authentication");
        assertThat(response.getClient().getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("getBountyById throws ResourceNotFoundException when not found")
    void testGetBountyById_NotFound() {
        when(bountyRepository.findWithDetailsById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bountyService.getBountyById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Bounty not found with id: '999'");
    }

    @Test
    @DisplayName("cancelBounty cancels OPEN bounty successfully by owner")
    void testCancelBounty_Success_ByOwner() {
        when(bountyRepository.findById(101L)).thenReturn(Optional.of(sampleBounty));
        when(bountyRepository.save(any(Bounty.class))).thenReturn(sampleBounty);

        BountyCancelResponse response = bountyService.cancelBounty(101L, clientPrincipal);

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getStatus()).isEqualTo(BountyStatus.CANCELLED);
        assertThat(sampleBounty.getStatus()).isEqualTo(BountyStatus.CANCELLED);
        verify(bountyRepository).save(sampleBounty);
    }

    @Test
    @DisplayName("cancelBounty cancels OPEN bounty successfully by administrator")
    void testCancelBounty_Success_ByAdmin() {
        when(bountyRepository.findById(101L)).thenReturn(Optional.of(sampleBounty));
        when(bountyRepository.save(any(Bounty.class))).thenReturn(sampleBounty);

        BountyCancelResponse response = bountyService.cancelBounty(101L, adminPrincipal);

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getStatus()).isEqualTo(BountyStatus.CANCELLED);
        verify(bountyRepository).save(sampleBounty);
    }

    @Test
    @DisplayName("cancelBounty throws ResourceNotFoundException when bounty does not exist")
    void testCancelBounty_NotFound() {
        when(bountyRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bountyService.cancelBounty(999L, clientPrincipal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Bounty not found with id: '999'");

        verify(bountyRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelBounty throws AccessDeniedException when caller is not the owner or admin")
    void testCancelBounty_NotOwner_ThrowsAccessDenied() {
        User otherClient = User.builder().id(999L).role(Role.ROLE_CLIENT).build();
        UserPrincipal otherClientPrincipal = UserPrincipal.fromClaims(999L, "Other", "other@acme.org", Role.ROLE_CLIENT);

        when(bountyRepository.findById(101L)).thenReturn(Optional.of(sampleBounty));

        assertThatThrownBy(() -> bountyService.cancelBounty(101L, otherClientPrincipal))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to cancel this bounty");

        verify(bountyRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelBounty throws InvalidStateTransitionException when already cancelled")
    void testCancelBounty_AlreadyCancelled_ThrowsInvalidStateTransition() {
        sampleBounty.setStatus(BountyStatus.CANCELLED);
        when(bountyRepository.findById(101L)).thenReturn(Optional.of(sampleBounty));

        assertThatThrownBy(() -> bountyService.cancelBounty(101L, clientPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Bounty is already cancelled");

        verify(bountyRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelBounty throws InvalidStateTransitionException when bounty is in ASSIGNED status")
    void testCancelBounty_AssignedStatus_ThrowsInvalidStateTransition() {
        sampleBounty.setStatus(BountyStatus.ASSIGNED);
        sampleBounty.setAssignedDeveloper(devUser);
        when(bountyRepository.findById(101L)).thenReturn(Optional.of(sampleBounty));

        assertThatThrownBy(() -> bountyService.cancelBounty(101L, clientPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Cannot transition Bounty from state 'ASSIGNED' to 'CANCELLED'");

        verify(bountyRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelBounty throws InvalidStateTransitionException when bounty is COMPLETED")
    void testCancelBounty_CompletedStatus_ThrowsInvalidStateTransition() {
        sampleBounty.setStatus(BountyStatus.COMPLETED);
        when(bountyRepository.findById(101L)).thenReturn(Optional.of(sampleBounty));

        assertThatThrownBy(() -> bountyService.cancelBounty(101L, clientPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Cannot transition Bounty from state 'COMPLETED' to 'CANCELLED'");

        verify(bountyRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelBounty cancels IN_REVIEW bounty successfully by owner")
    void testCancelBounty_Success_WhenInReview() {
        sampleBounty.setStatus(BountyStatus.IN_REVIEW);
        when(bountyRepository.findById(101L)).thenReturn(Optional.of(sampleBounty));
        when(bountyRepository.save(any(Bounty.class))).thenReturn(sampleBounty);

        BountyCancelResponse response = bountyService.cancelBounty(101L, clientPrincipal);

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getStatus()).isEqualTo(BountyStatus.CANCELLED);
        assertThat(sampleBounty.getStatus()).isEqualTo(BountyStatus.CANCELLED);
        verify(bountyRepository).save(sampleBounty);
    }

    @Test
    @DisplayName("cancelBounty throws InvalidStateTransitionException when bounty is IN_PROGRESS")
    void testCancelBounty_InProgressStatus_ThrowsInvalidStateTransition() {
        sampleBounty.setStatus(BountyStatus.IN_PROGRESS);
        when(bountyRepository.findById(101L)).thenReturn(Optional.of(sampleBounty));

        assertThatThrownBy(() -> bountyService.cancelBounty(101L, clientPrincipal))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("Cannot transition Bounty from state 'IN_PROGRESS' to 'CANCELLED'");

        verify(bountyRepository, never()).save(any());
    }
}
