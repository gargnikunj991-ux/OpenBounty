package com.openbounty.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbounty.dto.request.bounty.BountyCreateRequest;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.Role;
import com.openbounty.model.Bounty;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BountyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BountyRepository bountyRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User clientUser;
    private User otherClientUser;
    private User devUser;
    private String clientToken;
    private String otherClientToken;
    private String devToken;

    @BeforeEach
    void setUp() {
        bountyRepository.deleteAll();
        userRepository.deleteAll();

        clientUser = userRepository.save(User.builder()
                .name("Acme Corp")
                .email("lead@acmecorp.io")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_CLIENT)
                .reputationScore(100)
                .build());

        otherClientUser = userRepository.save(User.builder()
                .name("Beta Corp")
                .email("beta@corp.io")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_CLIENT)
                .reputationScore(40)
                .build());

        devUser = userRepository.save(User.builder()
                .name("Alex Solver")
                .email("alex@solver.dev")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(200)
                .build());

        clientToken = jwtService.generateToken(clientUser);
        otherClientToken = jwtService.generateToken(otherClientUser);
        devToken = jwtService.generateToken(devUser);
    }

    @Test
    @DisplayName("POST /api/bounties creates bounty successfully with ROLE_CLIENT")
    void testCreateBounty_Success() throws Exception {
        BountyCreateRequest request = BountyCreateRequest.builder()
                .title("Build Spring Security 6 Stateless JWT Module")
                .description("Detailed requirement description for building stateless JWT authentication.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .deadline(LocalDate.now().plusDays(15))
                .build();

        mockMvc.perform(post("/api/bounties")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Build Spring Security 6 Stateless JWT Module"))
                .andExpect(jsonPath("$.category").value("BACKEND_API"))
                .andExpect(jsonPath("$.rewardAmount").value(1500.00))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.client.id").value(clientUser.getId()))
                .andExpect(jsonPath("$.client.name").value("Acme Corp"));

        assertThat(bountyRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("POST /api/bounties returns 403 Forbidden when called by ROLE_DEVELOPER")
    void testCreateBounty_ForbiddenForDeveloper() throws Exception {
        BountyCreateRequest request = BountyCreateRequest.builder()
                .title("Build Spring Security 6 Stateless JWT Module")
                .description("Detailed requirement description.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .deadline(LocalDate.now().plusDays(15))
                .build();

        mockMvc.perform(post("/api/bounties")
                        .header("Authorization", "Bearer " + devToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/bounties returns 401 Unauthorized when no token is supplied")
    void testCreateBounty_Unauthorized() throws Exception {
        BountyCreateRequest request = BountyCreateRequest.builder()
                .title("Build Spring Security 6 Stateless JWT Module")
                .description("Detailed requirement description.")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .deadline(LocalDate.now().plusDays(15))
                .build();

        mockMvc.perform(post("/api/bounties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/bounties returns 400 Bad Request when request body fails validation")
    void testCreateBounty_ValidationError() throws Exception {
        BountyCreateRequest request = BountyCreateRequest.builder()
                .title("") // Blank title
                .description("Too short")
                .category(null)
                .rewardAmount(new BigDecimal("-50.00")) // Invalid amount
                .deadline(LocalDate.now().minusDays(1)) // Past deadline
                .build();

        mockMvc.perform(post("/api/bounties")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.rewardAmount").exists())
                .andExpect(jsonPath("$.errors.deadline").exists());
    }

    @Test
    @DisplayName("GET /api/bounties returns paginated list of bounties publicly")
    void testGetBounties_Public_Success() throws Exception {
        bountyRepository.save(Bounty.builder()
                .title("Build React Frontend")
                .description("Frontend task")
                .category(BountyCategory.WEB_DEVELOPMENT)
                .rewardAmount(new BigDecimal("800.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(20))
                .client(clientUser)
                .build());

        bountyRepository.save(Bounty.builder()
                .title("Build Spring Security Auth")
                .description("Backend auth task")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1200.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .build());

        mockMvc.perform(get("/api/bounties")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.pageNumber").value(0));
    }

    @Test
    @DisplayName("GET /api/bounties filters by category and keyword search")
    void testGetBounties_WithFilterAndSearch() throws Exception {
        bountyRepository.save(Bounty.builder()
                .title("Build React Frontend")
                .description("Frontend task")
                .category(BountyCategory.WEB_DEVELOPMENT)
                .rewardAmount(new BigDecimal("800.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(20))
                .client(clientUser)
                .build());

        bountyRepository.save(Bounty.builder()
                .title("Build Spring Security Auth")
                .description("Backend auth task with JWT")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1200.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .build());

        mockMvc.perform(get("/api/bounties")
                        .param("category", "BACKEND_API")
                        .param("search", "Spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Build Spring Security Auth"))
                .andExpect(jsonPath("$.content[0].category").value("BACKEND_API"));
    }

    @Test
    @DisplayName("GET /api/bounties/{id} returns full bounty details publicly")
    void testGetBountyById_Success() throws Exception {
        Bounty saved = bountyRepository.save(Bounty.builder()
                .title("Build Spring Security Auth")
                .description("Full specification description here")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1200.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .build());

        mockMvc.perform(get("/api/bounties/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.title").value("Build Spring Security Auth"))
                .andExpect(jsonPath("$.description").value("Full specification description here"))
                .andExpect(jsonPath("$.client.id").value(clientUser.getId()));
    }

    @Test
    @DisplayName("GET /api/bounties/{id} returns 404 when bounty does not exist")
    void testGetBountyById_NotFound() throws Exception {
        mockMvc.perform(get("/api/bounties/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("PATCH /api/bounties/{id}/cancel cancels bounty by owner")
    void testCancelBounty_Success() throws Exception {
        Bounty saved = bountyRepository.save(Bounty.builder()
                .title("Cancelable Bounty")
                .description("To be cancelled")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("500.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .build());

        mockMvc.perform(patch("/api/bounties/" + saved.getId() + "/cancel")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.message").value(containsString("successfully cancelled")));

        Bounty updated = bountyRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BountyStatus.CANCELLED);
    }

    @Test
    @DisplayName("PATCH /api/bounties/{id}/cancel returns 403 when called by another client")
    void testCancelBounty_ForbiddenForOtherClient() throws Exception {
        Bounty saved = bountyRepository.save(Bounty.builder()
                .title("Acme Bounty")
                .description("Owned by Acme")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("500.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .build());

        mockMvc.perform(patch("/api/bounties/" + saved.getId() + "/cancel")
                        .header("Authorization", "Bearer " + otherClientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"))
                .andExpect(jsonPath("$.detail").value(containsString("Only the bounty creator can cancel it")));
    }

    @Test
    @DisplayName("PATCH /api/bounties/{id}/cancel returns 409 Conflict when already cancelled")
    void testCancelBounty_AlreadyCancelled_ReturnsConflict() throws Exception {
        Bounty saved = bountyRepository.save(Bounty.builder()
                .title("Already Cancelled")
                .description("Already cancelled")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("500.00"))
                .status(BountyStatus.CANCELLED)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .build());

        mockMvc.perform(patch("/api/bounties/" + saved.getId() + "/cancel")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid State Transition"))
                .andExpect(jsonPath("$.detail").value(containsString("already cancelled")));
    }

    @Test
    @DisplayName("PATCH /api/bounties/{id}/cancel succeeds when bounty is IN_REVIEW")
    void testCancelBounty_InReviewStatus_Success() throws Exception {
        Bounty saved = bountyRepository.save(Bounty.builder()
                .title("In Review Bounty")
                .description("Proposals are being evaluated")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("900.00"))
                .status(BountyStatus.IN_REVIEW)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .build());

        mockMvc.perform(patch("/api/bounties/" + saved.getId() + "/cancel")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("PATCH /api/bounties/{id}/cancel returns 409 Conflict when bounty is IN_PROGRESS")
    void testCancelBounty_InProgressStatus_ReturnsConflict() throws Exception {
        Bounty saved = bountyRepository.save(Bounty.builder()
                .title("In Progress Bounty")
                .description("Work actively underway")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1200.00"))
                .status(BountyStatus.IN_PROGRESS)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .assignedDeveloper(devUser)
                .build());

        mockMvc.perform(patch("/api/bounties/" + saved.getId() + "/cancel")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid State Transition"))
                .andExpect(jsonPath("$.detail").value(containsString("Cannot transition Bounty from state 'IN_PROGRESS' to 'CANCELLED'")));
    }

    @Test
    @DisplayName("PATCH /api/bounties/{id}/cancel returns 403 Forbidden when called by ROLE_DEVELOPER")
    void testCancelBounty_ForbiddenForDeveloper() throws Exception {
        Bounty saved = bountyRepository.save(Bounty.builder()
                .title("Open Bounty")
                .description("Open for bids")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("500.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(10))
                .client(clientUser)
                .build());

        mockMvc.perform(patch("/api/bounties/" + saved.getId() + "/cancel")
                        .header("Authorization", "Bearer " + devToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/bounties returns 400 Bad Request when status query param is invalid enum")
    void testGetBounties_InvalidStatusParam_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/bounties")
                        .param("status", "NON_EXISTENT_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Parameter Type"));
    }

    @Test
    @DisplayName("POST /api/bounties fails boundary validation when reward is $0.99")
    void testCreateBounty_BoundaryReward_99Cents_FailsValidation() throws Exception {
        BountyCreateRequest request = BountyCreateRequest.builder()
                .title("Valid Title Here")
                .description("Valid description with enough characters")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("0.99")) // Below 1.00 minimum
                .deadline(LocalDate.now().plusDays(10))
                .build();

        mockMvc.perform(post("/api/bounties")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rewardAmount").value("Reward amount must be greater than zero"));
    }

    @Test
    @DisplayName("POST /api/bounties passes boundary validation when reward is exactly $1.00")
    void testCreateBounty_BoundaryReward_OneDollar_PassesValidation() throws Exception {
        BountyCreateRequest request = BountyCreateRequest.builder()
                .title("Valid Title Here")
                .description("Valid description with enough characters")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1.00")) // Exactly at minimum
                .deadline(LocalDate.now().plusDays(10))
                .build();

        mockMvc.perform(post("/api/bounties")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rewardAmount").value(1.00));
    }
}
