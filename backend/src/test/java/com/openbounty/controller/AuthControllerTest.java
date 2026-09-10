package com.openbounty.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbounty.dto.request.auth.LoginRequest;
import com.openbounty.dto.request.auth.RefreshTokenRequest;
import com.openbounty.dto.request.auth.RegisterRequest;
import com.openbounty.enums.Role;
import com.openbounty.repository.RefreshTokenRepository;
import com.openbounty.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/auth/register creates user and returns 201 Created")
    void testRegister_Success() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name("Alex Johnson")
                .email("alex.johnson@example.com")
                .password("SecurePassword123!")
                .role(Role.ROLE_DEVELOPER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Alex Johnson"))
                .andExpect(jsonPath("$.email").value("alex.johnson@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_DEVELOPER"))
                .andExpect(jsonPath("$.reputationScore").value(0));

        assertThat(userRepository.findByEmail("alex.johnson@example.com")).isPresent();
    }

    @Test
    @DisplayName("POST /api/auth/register returns 409 CONFLICT on duplicate email")
    void testRegister_DuplicateEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name("Alex Johnson")
                .email("alex.johnson@example.com")
                .password("SecurePassword123!")
                .role(Role.ROLE_DEVELOPER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Resource Conflict"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("alex.johnson@example.com")));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 BAD REQUEST when registering as ROLE_ADMIN")
    void testRegister_AdminForbidden() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name("Super Admin")
                .email("admin@example.com")
                .password("SecurePassword123!")
                .role(Role.ROLE_ADMIN)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ROLE_ADMIN")));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 BAD REQUEST when input fields are invalid")
    void testRegister_ValidationFailure() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name("")
                .email("invalid-email-format")
                .password("short")
                .role(null)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.role").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 OK and valid JWT token")
    void testLogin_Success() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .name("Alex Johnson")
                .email("alex.johnson@example.com")
                .password("SecurePassword123!")
                .role(Role.ROLE_DEVELOPER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = LoginRequest.builder()
                .email("alex.johnson@example.com")
                .password("SecurePassword123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.expiresInMs").value(600000L))
                .andExpect(jsonPath("$.user.email").value("alex.johnson@example.com"))
                .andExpect(jsonPath("$.user.name").value("Alex Johnson"))
                .andExpect(jsonPath("$.user.role").value("ROLE_DEVELOPER"));
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 UNAUTHORIZED on wrong password")
    void testLogin_WrongPassword() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .name("Alex Johnson")
                .email("alex.johnson@example.com")
                .password("SecurePassword123!")
                .role(Role.ROLE_DEVELOPER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = LoginRequest.builder()
                .email("alex.johnson@example.com")
                .password("WrongPassword123!")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 200 OK with authenticated user profile")
    void testGetCurrentUser_Success() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .name("Alex Johnson")
                .email("alex.johnson@example.com")
                .password("SecurePassword123!")
                .role(Role.ROLE_DEVELOPER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = LoginRequest.builder()
                .email("alex.johnson@example.com")
                .password("SecurePassword123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = responseJson.get("token").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alex Johnson"))
                .andExpect(jsonPath("$.email").value("alex.johnson@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_DEVELOPER"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 401 UNAUTHORIZED when no token is supplied")
    void testGetCurrentUser_MissingToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 401 UNAUTHORIZED when token is invalid")
    void testGetCurrentUser_InvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.token.string"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh returns new access token and rotated refresh token")
    void testRefreshToken_Success() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .name("Alex Johnson")
                .email("alex.johnson@example.com")
                .password("SecurePassword123!")
                .role(Role.ROLE_DEVELOPER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = LoginRequest.builder()
                .email("alex.johnson@example.com")
                .password("SecurePassword123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String initialRefreshToken = loginJson.get("refreshToken").asText();

        // Refresh tokens
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(initialRefreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.expiresInMs").value(600000L))
                .andReturn();

        JsonNode refreshJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newAccessToken = refreshJson.get("accessToken").asText();
        String rotatedRefreshToken = refreshJson.get("refreshToken").asText();

        // Verify the rotated refresh token is distinct from initial
        assertThat(rotatedRefreshToken).isNotEqualTo(initialRefreshToken);

        // Verify the new access token can access /api/auth/me
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alex.johnson@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh detects reused revoked token and blocks")
    void testRefreshToken_ReplayAttack_ReuseDetection() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .name("Alex Johnson")
                .email("alex.johnson@example.com")
                .password("SecurePassword123!")
                .role(Role.ROLE_DEVELOPER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = LoginRequest.builder()
                .email("alex.johnson@example.com")
                .password("SecurePassword123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String initialRefreshToken = loginJson.get("refreshToken").asText();

        // First refresh succeeds and rotates initialRefreshToken
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(initialRefreshToken);
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk());

        // Reusing the old initialRefreshToken triggers reuse detection
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Revoked refresh token reuse detected")));
    }

    @Test
    @DisplayName("POST /api/auth/logout revokes refresh token so subsequent refresh fails")
    void testLogout_Success() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .name("Alex Johnson")
                .email("alex.johnson@example.com")
                .password("SecurePassword123!")
                .role(Role.ROLE_DEVELOPER)
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        LoginRequest loginRequest = LoginRequest.builder()
                .email("alex.johnson@example.com")
                .password("SecurePassword123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String refreshToken = loginJson.get("refreshToken").asText();

        // Logout
        RefreshTokenRequest logoutRequest = new RefreshTokenRequest(refreshToken);
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Successfully logged out. Refresh token revoked."));

        // Attempt to refresh with logged-out token
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }
}
