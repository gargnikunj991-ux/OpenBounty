package com.openbounty.service;

import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.EntryType;
import com.openbounty.enums.LedgerAccount;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.enums.Role;
import com.openbounty.enums.TransactionType;
import com.openbounty.model.Bounty;
import com.openbounty.model.LedgerTransaction;
import com.openbounty.model.Milestone;
import com.openbounty.model.Proposal;
import com.openbounty.model.User;
import com.openbounty.repository.BountyRepository;
import com.openbounty.repository.MilestoneRepository;
import com.openbounty.repository.ProposalRepository;
import com.openbounty.repository.UserRepository;
import com.openbounty.repository.projection.AccountBalanceProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("EscrowLedgerService Integration Tests")
class EscrowLedgerServiceTest {

    @Autowired
    private EscrowLedgerService escrowLedgerService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BountyRepository bountyRepository;

    @Autowired
    private ProposalRepository proposalRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    private User client;
    private User developer;
    private Bounty bounty;
    private Proposal proposal;
    private Milestone milestone1;
    private Milestone milestone2;

    @BeforeEach
    void setUp() {
        client = userRepository.save(User.builder()
                .name("Fintech Client Corp")
                .email("client-" + System.nanoTime() + "@acme.org")
                .password("hash123456")
                .role(Role.ROLE_CLIENT)
                .reputationScore(50)
                .build());

        developer = userRepository.save(User.builder()
                .name("Alex Solver")
                .email("solver-" + System.nanoTime() + "@dev.org")
                .password("hash123456")
                .role(Role.ROLE_DEVELOPER)
                .reputationScore(100)
                .build());

        bounty = bountyRepository.save(Bounty.builder()
                .title("Build Double-Entry Accounting Engine")
                .description("Production-grade financial ledger with ACID guarantees")
                .category(BountyCategory.BACKEND_API)
                .rewardAmount(new BigDecimal("1000.00"))
                .deadline(LocalDate.now().plusDays(14))
                .status(BountyStatus.OPEN)
                .client(client)
                .build());

        proposal = proposalRepository.save(Proposal.builder()
                .bounty(bounty)
                .developer(developer)
                .proposedAmount(new BigDecimal("1000.00"))
                .estimatedDays(7)
                .approachDescription("Using JPA and pessimistic locking")
                .status(ProposalStatus.ACCEPTED)
                .build());

        milestone1 = milestoneRepository.save(Milestone.builder()
                .proposal(proposal)
                .title("Milestone 1: Chart of accounts & domain entities")
                .status(MilestoneStatus.SUBMITTED)
                .build());

        milestone2 = milestoneRepository.save(Milestone.builder()
                .proposal(proposal)
                .title("Milestone 2: Escrow lifecycle & disbursement")
                .status(MilestoneStatus.PENDING)
                .build());
    }

    @Test
    @DisplayName("Should lock bounty escrow upon publication with exact debit and credit entries")
    void shouldLockBountyEscrow() {
        LedgerTransaction txn = escrowLedgerService.lockBountyEscrow(bounty);

        assertThat(txn).isNotNull();
        assertThat(txn.getTransactionType()).isEqualTo(TransactionType.BOUNTY_ESCROW_LOCK);
        assertThat(txn.isBalanced()).isTrue();
        assertThat(txn.calculateTotalDebit()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(txn.calculateTotalCredit()).isEqualByComparingTo(new BigDecimal("1000.00"));

        BigDecimal lockedEscrow = escrowLedgerService.getLockedEscrowForBounty(bounty.getId());
        assertThat(lockedEscrow).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Should disburse milestone payout splitting developer earnings (85%) and platform take-rate (15%)")
    void shouldDisburseMilestonePayoutWithSplit() {
        // Step 1: Lock initial $1,000 escrow
        escrowLedgerService.lockBountyEscrow(bounty);

        // Step 2: Disburse payout for milestone 1 ($500 gross)
        LedgerTransaction payoutTxn = escrowLedgerService.disburseMilestonePayout(milestone1, new BigDecimal("0.15"));

        assertThat(payoutTxn).isNotNull();
        assertThat(payoutTxn.getTransactionType()).isEqualTo(TransactionType.MILESTONE_PAYOUT_RELEASE);
        assertThat(payoutTxn.isBalanced()).isTrue();

        // Gross = $500, Developer (85%) = $425, Platform (15%) = $75
        assertThat(payoutTxn.calculateTotalDebit()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(payoutTxn.calculateTotalCredit()).isEqualByComparingTo(new BigDecimal("500.00"));

        // Remaining locked escrow should now be $500.00
        BigDecimal remainingLocked = escrowLedgerService.getLockedEscrowForBounty(bounty.getId());
        assertThat(remainingLocked).isEqualByComparingTo(new BigDecimal("500.00"));

        // Developer payable should be $425.00
        BigDecimal devPayable = escrowLedgerService.getDeveloperPayableBalance(developer.getId());
        assertThat(devPayable).isEqualByComparingTo(new BigDecimal("425.00"));
    }

    @Test
    @DisplayName("Should refund remaining locked escrow upon bounty cancellation")
    void shouldRefundLockedEscrowOnCancellation() {
        // Step 1: Lock $1,000 escrow
        escrowLedgerService.lockBountyEscrow(bounty);

        // Step 2: Refund escrow
        LedgerTransaction refundTxn = escrowLedgerService.refundBountyEscrow(bounty, "Cancelled by client");

        assertThat(refundTxn).isNotNull();
        assertThat(refundTxn.getTransactionType()).isEqualTo(TransactionType.BOUNTY_ESCROW_REFUND);
        assertThat(refundTxn.isBalanced()).isTrue();
        assertThat(refundTxn.calculateTotalDebit()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(refundTxn.calculateTotalCredit()).isEqualByComparingTo(new BigDecimal("1000.00"));

        // Remaining locked escrow should now be $0.00
        BigDecimal remainingLocked = escrowLedgerService.getLockedEscrowForBounty(bounty.getId());
        assertThat(remainingLocked).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should maintain GAAP balance sheet mathematical equation at every step of lifecycle")
    void shouldPreserveGAAPBalanceSheetInvariants() {
        // 1. Lock $1,000 escrow
        escrowLedgerService.lockBountyEscrow(bounty);

        List<AccountBalanceProjection> balancesStage1 = escrowLedgerService.getPlatformBalanceSheet();
        BigDecimal cash1 = getBalance(balancesStage1, LedgerAccount.PLATFORM_CASH);
        BigDecimal escrow1 = getBalance(balancesStage1, LedgerAccount.CLIENT_ESCROW_LOCKED);
        assertThat(cash1).isEqualByComparingTo(escrow1);

        // 2. Disburse Milestone 1 payout ($500 gross)
        escrowLedgerService.disburseMilestonePayout(milestone1, new BigDecimal("0.15"));

        List<AccountBalanceProjection> balancesStage2 = escrowLedgerService.getPlatformBalanceSheet();
        BigDecimal cash2 = getBalance(balancesStage2, LedgerAccount.PLATFORM_CASH);
        BigDecimal escrow2 = getBalance(balancesStage2, LedgerAccount.CLIENT_ESCROW_LOCKED);
        BigDecimal payable2 = getBalance(balancesStage2, LedgerAccount.DEVELOPER_PAYABLE);
        BigDecimal revenue2 = getBalance(balancesStage2, LedgerAccount.PLATFORM_FEE_REVENUE);

        // Assets (Cash) == Liabilities (Escrow + Developer Payable) + Revenue
        // 1000 == 500 + 425 + 75 == 1000
        assertThat(cash2).isEqualByComparingTo(escrow2.add(payable2).add(revenue2));
    }

    private BigDecimal getBalance(List<AccountBalanceProjection> balances, LedgerAccount account) {
        return balances.stream()
                .filter(b -> b.getAccount() == account)
                .findFirst()
                .map(AccountBalanceProjection::getNetBalance)
                .orElse(BigDecimal.ZERO);
    }
}
