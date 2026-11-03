package com.openbounty.controller;

import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.Role;
import com.openbounty.model.Bounty;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.MilestoneRepository;
import com.openbounty.repository.ProposalRepository;
import com.openbounty.repository.RefreshTokenRepository;
import com.openbounty.repository.ReviewRepository;
import com.openbounty.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BountyRepository bountyRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private User client;
    private User developer;
    private Bounty completedBounty;
    private Bounty openBounty;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        milestoneRepository.deleteAll();
        proposalRepository.deleteAll();
        bountyRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        client = userRepository.save(User.builder()
                .name("Acme Corp")
                .email("client@acme.org")
                .password("hashed_pwd_123")
                .role(Role.ROLE_CLIENT)
                .reputationScore(50)
                .build());

        developer = userRepository.save(User.builder()
                .name("Alice Solver")
                .email("alice@openbounty.dev")
                .password("hashed_pwd_456")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(100)
                .build());

        completedBounty = bountyRepository.save(Bounty.builder()
                .title("Completed Security Audit")
                .description("Audit JWT authentication")
                .category(BountyCategory.SECURITY_AUDIT)
                .rewardAmount(new BigDecimal("3000.00"))
                .status(BountyStatus.COMPLETED)
                .deadline(LocalDate.now().minusDays(5))
                .client(client)
                .assignedDeveloper(developer)
                .build());

        openBounty = bountyRepository.save(Bounty.builder()
                .title("Open Backend API Challenge")
                .description("Build Kafka streaming pipeline")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1500.00"))
                .status(BountyStatus.OPEN)
                .deadline(LocalDate.now().plusDays(10))
                .client(client)
                .assignedDeveloper(null)
                .build());
    }

    @Test
    @DisplayName("GET /api/analytics/overview: Returns public platform overview metrics with 200 OK")
    void testGetOverview_Success() throws Exception {
        mockMvc.perform(get("/api/analytics/overview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBounties").value(2))
                .andExpect(jsonPath("$.activeBounties").value(1))
                .andExpect(jsonPath("$.completedBounties").value(1))
                .andExpect(jsonPath("$.totalFundsDisbursed").value(3000.00))
                .andExpect(jsonPath("$.totalDevelopers").value(1))
                .andExpect(jsonPath("$.totalClients").value(1));
    }

    @Test
    @DisplayName("GET /api/analytics/categories: Returns category distribution breakdown with 200 OK")
    void testGetCategoryDistribution_Success() throws Exception {
        mockMvc.perform(get("/api/analytics/categories")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }
}
