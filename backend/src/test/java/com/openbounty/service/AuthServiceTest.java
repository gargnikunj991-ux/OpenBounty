package com.openbounty.service;

import com.openbounty.dto.request.auth.LoginRequest;
import com.openbounty.dto.request.auth.RefreshTokenRequest;
import com.openbounty.dto.request.auth.RegisterRequest;
import com.openbounty.dto.response.auth.AuthResponse;
import com.openbounty.dto.response.auth.TokenRefreshResponse;
import com.openbounty.dto.response.auth.UserProfileResponse;
import com.openbounty.enums.Role;
import com.openbounty.exception.BadRequestException;
import com.openbounty.exception.DuplicateResourceException;
import com.openbounty.exception.ResourceNotFoundException;
import com.openbounty.exception.UnauthorizedException;
import com.openbounty.model.RefreshToken;
import com.openbounty.model.User;
import com.openbounty.repository.UserRepository;
import com.openbounty.security.JwtService;
import com.openbounty.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;
    private UserPrincipal samplePrincipal;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .name("Alex Johnson")
                .email("alex@example.com")
                .password("encoded_secret_hash")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(0)
                .createdAt(LocalDateTime.now())
                .build();

        samplePrincipal = UserPrincipal.create(sampleUser);
    }

    @Test
    @DisplayName("register creates new user with encoded password and returns profile")
    void testRegister_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Alex Johnson")
                .email("Alex@Example.com")
                .password("RawPassword123!")
                .role(Role.ROLE_DEVELOPER)
                .build();

        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(passwordEncoder.encode("RawPassword123!")).thenReturn("encoded_secret_hash");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(sampleUser);

        UserProfileResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Alex Johnson");
        assertThat(response.getEmail()).isEqualTo("alex@example.com");
        assertThat(response.getRole()).isEqualTo(Role.ROLE_DEVELOPER);
        assertThat(response.getReputationScore()).isZero();

        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("register throws DuplicateResourceException when email already exists")
    void testRegister_DuplicateEmail() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Alex Johnson")
                .email("alex@example.com")
                .password("RawPassword123!")
                .role(Role.ROLE_DEVELOPER)
                .build();

        when(userRepository.existsByEmail("alex@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("User already exists with email: 'alex@example.com'");
    }

    @Test
    @DisplayName("register throws BadRequestException when trying to register directly as ROLE_ADMIN")
    void testRegister_AdminRoleForbidden() {
        RegisterRequest request = RegisterRequest.builder()
                .name("Admin User")
                .email("admin@example.com")
                .password("RawPassword123!")
                .role(Role.ROLE_ADMIN)
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ROLE_ADMIN");
    }

    @Test
    @DisplayName("login authenticates credentials and returns JWT AuthResponse")
    void testLogin_Success() {
        LoginRequest request = LoginRequest.builder()
                .email("alex@example.com")
                .password("RawPassword123!")
                .build();

        RefreshToken mockRefreshToken = RefreshToken.builder()
                .id(10L)
                .user(sampleUser)
                .token("mocked.refresh.token")
                .build();

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateToken(sampleUser)).thenReturn("mocked.jwt.token");
        when(jwtService.getExpirationMs()).thenReturn(600000L);
        when(refreshTokenService.createRefreshToken(sampleUser)).thenReturn(mockRefreshToken);

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mocked.jwt.token");
        assertThat(response.getRefreshToken()).isEqualTo("mocked.refresh.token");
        assertThat(response.getType()).isEqualTo("Bearer");
        assertThat(response.getExpiresInMs()).isEqualTo(600000L);
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getId()).isEqualTo(1L);
        assertThat(response.getUser().getEmail()).isEqualTo("alex@example.com");

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("refreshToken rotates token and returns TokenRefreshResponse")
    void testRefreshToken_Success() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid.refresh.token");
        RefreshToken rotatedToken = RefreshToken.builder()
                .id(11L)
                .user(sampleUser)
                .token("new.refresh.token")
                .build();

        when(refreshTokenService.rotateRefreshToken("valid.refresh.token")).thenReturn(rotatedToken);
        when(jwtService.generateToken(sampleUser)).thenReturn("new.access.token");
        when(jwtService.getExpirationMs()).thenReturn(600000L);

        TokenRefreshResponse response = authService.refreshToken(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new.access.token");
        assertThat(response.getRefreshToken()).isEqualTo("new.refresh.token");
        assertThat(response.getExpiresInMs()).isEqualTo(600000L);
    }

    @Test
    @DisplayName("logout revokes provided refresh token (single-device logout)")
    void testLogout_SingleDevice_Success() {
        RefreshTokenRequest request = new RefreshTokenRequest("active.refresh.token");

        authService.logout(request, samplePrincipal);

        verify(refreshTokenService).revokeToken("active.refresh.token");
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    @DisplayName("logout with allDevices flag revokes all user sessions")
    void testLogout_AllDevices_Success() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("active.refresh.token")
                .allDevices(true)
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        authService.logout(request, samplePrincipal);

        verify(refreshTokenService).revokeAllForUser(sampleUser);
    }

    @Test
    @DisplayName("logout without refresh token revokes all user sessions when authenticated")
    void testLogout_WithoutToken_RevokesAll() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        authService.logout(null, samplePrincipal);

        verify(refreshTokenService).revokeAllForUser(sampleUser);
    }

    @Test
    @DisplayName("login throws UnauthorizedException when credentials are invalid")
    void testLogin_InvalidCredentials() {
        LoginRequest request = LoginRequest.builder()
                .email("alex@example.com")
                .password("WrongPassword")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("login throws UnauthorizedException when user not found after authentication")
    void testLogin_UserNotFound() {
        LoginRequest request = LoginRequest.builder()
                .email("alex@example.com")
                .password("RawPassword123!")
                .build();

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("getCurrentUserProfile returns profile for authenticated user")
    void testGetCurrentUserProfile_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        UserProfileResponse response = authService.getCurrentUserProfile(samplePrincipal);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("alex@example.com");
    }

    @Test
    @DisplayName("getCurrentUserProfile throws UnauthorizedException when principal is null")
    void testGetCurrentUserProfile_NullPrincipal() {
        assertThatThrownBy(() -> authService.getCurrentUserProfile(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("getCurrentUserProfile throws ResourceNotFoundException when user record is missing")
    void testGetCurrentUserProfile_UserNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getCurrentUserProfile(samplePrincipal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with id: '1'");
    }

    @Test
    @DisplayName("getCurrentUserEntity returns User entity for internal service transactions")
    void testGetCurrentUserEntity_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        User user = authService.getCurrentUserEntity(samplePrincipal);

        assertThat(user).isNotNull();
        assertThat(user.getId()).isEqualTo(1L);
    }
}
