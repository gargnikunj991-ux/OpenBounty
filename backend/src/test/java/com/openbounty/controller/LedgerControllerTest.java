package com.openbounty.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.model.Bounty;
import com.openbounty.model.Milestone;
import com.openbounty.model.Proposal;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.LedgerEntryRepository;
import com.openbounty.repository.LedgerTransactionRepository;
import com.openbounty.repository.MilestoneRepository;
import com.openbounty.repository.ProposalRepository;
import com.openbounty.repository.RefreshTokenRepository;
import com.openbounty.repository.ReviewRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.security.JwtService;
import com.openbounty.service.EscrowLedgerService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("LedgerController & Escrow Security Integration Tests")
class LedgerControllerTest {

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

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private EscrowLedgerService escrowLedgerService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User adminUser;
    private User clientUser;
    private User devUser;
    private User strangerUser;

    private String adminToken;
    private String clientToken;
    private String devToken;
    private String strangerToken;

    private Bounty bounty;
    private Proposal proposal;
    private Milestone milestone;

    @BeforeEach
    void setUp() {
        ledgerEntryRepository.deleteAll();
        ledgerTransactionRepository.deleteAll();
        milestoneRepository.deleteAll();
        proposalRepository.deleteAll();
        reviewRepository.deleteAll();
        bountyRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(User.builder()
                .name("Super Admin")
                .email("admin@openbounty.dev")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_ADMIN)
                .reputationScore(500)
                .build());

        clientUser = userRepository.save(User.builder()
                .name("Fintech Client")
                .email("client@acme.org")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_CLIENT)
                .reputationScore(100)
                .build());

        devUser = userRepository.save(User.builder()
                .name("Alex Developer")
                .email("alex@solver.dev")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(150)
                .build());

        strangerUser = userRepository.save(User.builder()
                .name("Unrelated Dev")
                .email("stranger@hacker.io")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(10)
                .build());

        adminToken = "Bearer " + jwtService.generateToken(adminUser);
        clientToken = "Bearer " + jwtService.generateToken(clientUser);
        devToken = "Bearer " + jwtService.generateToken(devUser);
        strangerToken = "Bearer " + jwtService.generateToken(strangerUser);

        bounty = bountyRepository.save(Bounty.builder()
                .title("Build Secure Financial Ledger")
                .description("Production-grade GAAP escrow double entry module")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("2000.00"))
                .deadline(LocalDate.now().plusDays(20))
                .status(BountyStatus.ASSIGNED)
                .client(clientUser)
                .assignedDeveloper(devUser)
                .build());

        proposal = proposalRepository.save(Proposal.builder()
                .bounty(bounty)
                .developer(devUser)
                .proposedAmount(new BigDecimal("2000.00"))
                .estimatedDays(10)
                .approachDescription("Using Spring Data JPA and double-entry accounts")
                .status(ProposalStatus.ACCEPTED)
                .build());

        milestone = milestoneRepository.save(Milestone.builder()
                .proposal(proposal)
                .title("Milestone 1: Double-Entry Core Engine")
                .status(MilestoneStatus.SUBMITTED)
                .build());

        // Lock escrow in ledger
        escrowLedgerService.lockBountyEscrow(bounty);
    }

    @Test
    @DisplayName("Admin can fetch platform balance sheet audit report (200 OK)")
    void adminCanGetBalanceSheet() throws Exception {
        mockMvc.perform(get("/api/ledger/balance-sheet")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced", is(true)))
                .andExpect(jsonPath("$.platformCash").value(2000.0))
                .andExpect(jsonPath("$.clientEscrowLocked").value(2000.0));
    }

    @Test
    @DisplayName("Non-admin role is rejected when requesting balance sheet (403 Forbidden)")
    void nonAdminCannotGetBalanceSheet() throws Exception {
        mockMvc.perform(get("/api/ledger/balance-sheet")
                        .header("Authorization", devToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/ledger/balance-sheet")
                        .header("Authorization", clientToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Admin can fetch paginated transactions audit trail (200 OK)")
    void adminCanGetTransactions() throws Exception {
        mockMvc.perform(get("/api/ledger/transactions")
                        .header("Authorization", adminToken)
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].transactionType").value("BOUNTY_ESCROW_LOCK"))
                .andExpect(jsonPath("$.content[0].balanced", is(true)));
    }

    @Test
    @DisplayName("Bounty client owner and assigned developer can view bounty escrow summary (200 OK)")
    void participantsCanGetBountyEscrowSummary() throws Exception {
        // Client owner check
        mockMvc.perform(get("/api/ledger/escrow/bounties/" + bounty.getId())
                        .header("Authorization", clientToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bountyId").value(bounty.getId()))
                .andExpect(jsonPath("$.currentLockedEscrow").value(2000.0));

        // Assigned developer check
        mockMvc.perform(get("/api/ledger/escrow/bounties/" + bounty.getId())
                        .header("Authorization", devToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bountyId").value(bounty.getId()))
                .andExpect(jsonPath("$.currentLockedEscrow").value(2000.0));
    }

    @Test
    @DisplayName("IDOR Defense: Unrelated third-party user cannot view bounty escrow summary (403 Forbidden)")
    void strangerCannotGetBountyEscrowSummary() throws Exception {
        mockMvc.perform(get("/api/ledger/escrow/bounties/" + bounty.getId())
                        .header("Authorization", strangerToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));
    }

    @Test
    @DisplayName("Developer can check own payable balance after milestone disbursement")
    void developerCanCheckPayableBalance() throws Exception {
        // Disburse milestone payout ($2000 gross -> $1700 dev net, $300 platform fee)
        escrowLedgerService.disburseMilestonePayout(milestone, new BigDecimal("0.15"));

        mockMvc.perform(get("/api/ledger/payable/me")
                        .header("Authorization", devToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(devUser.getId()))
                .andExpect(jsonPath("$.payableBalance").value(1700.0));
    }

    @Test
    @DisplayName("Concurrency: Multithreaded concurrent escrow operations preserve 100% debit-credit ledger balance")
    void concurrentEscrowOperationsPreserveLedgerBalance() throws Exception {
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    Bounty b = bountyRepository.save(Bounty.builder()
                            .title("Concurrent Bounty #" + index)
                            .description("Bounty for concurrency test")
                            .category(BountyCategory.BACKEND_API)
                            .rewardAmount(new BigDecimal("500.00"))
                            .deadline(LocalDate.now().plusDays(10))
                            .status(BountyStatus.OPEN)
                            .client(clientUser)
                            .build());

                    escrowLedgerService.lockBountyEscrow(b);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Log error if any
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(threads);

        // Verify balance sheet audit: Total Assets must exactly equal Total Liabilities + Revenue
        mockMvc.perform(get("/api/ledger/balance-sheet")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanced", is(true)));
    }
}
