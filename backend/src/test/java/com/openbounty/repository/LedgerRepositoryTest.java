package com.openbounty.repository;

import com.openbounty.enums.EntryType;
import com.openbounty.enums.LedgerAccount;
import com.openbounty.enums.TransactionType;
import com.openbounty.model.LedgerEntry;
import com.openbounty.model.LedgerTransaction;
import com.openbounty.repository.projection.AccountBalanceProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("Ledger Repositories Integration Tests")
class LedgerRepositoryTest {

    @Autowired
    private LedgerTransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository entryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long sampleBountyId = 100L;
    private Long sampleMilestoneId = 200L;
    private Long sampleUserId = 300L;

    @BeforeEach
    void setUp() {
        // Create an initial balanced transaction: Escrow Lock ($1000)
        LedgerTransaction lockTxn = LedgerTransaction.builder()
                .transactionReference("TXN-LOCK-100")
                .transactionType(TransactionType.BOUNTY_ESCROW_LOCK)
                .description("Escrow deposit for bounty #100")
                .bountyId(sampleBountyId)
                .userId(sampleUserId)
                .entries(new ArrayList<>())
                .build();

        lockTxn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.PLATFORM_CASH)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("1000.00"))
                .build());

        lockTxn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.CLIENT_ESCROW_LOCKED)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("1000.00"))
                .build());

        transactionRepository.save(lockTxn);

        // Create second balanced transaction: Milestone 1 Payout Release ($500)
        LedgerTransaction releaseTxn = LedgerTransaction.builder()
                .transactionReference("TXN-REL-200")
                .transactionType(TransactionType.MILESTONE_PAYOUT_RELEASE)
                .description("Payout for milestone #200")
                .bountyId(sampleBountyId)
                .milestoneId(sampleMilestoneId)
                .userId(sampleUserId)
                .entries(new ArrayList<>())
                .build();

        releaseTxn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.CLIENT_ESCROW_LOCKED)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("500.00"))
                .build());

        releaseTxn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.DEVELOPER_PAYABLE)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("425.00"))
                .build());

        releaseTxn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.PLATFORM_FEE_REVENUE)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("75.00"))
                .build());

        transactionRepository.save(releaseTxn);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Should fetch transaction with entries by reference")
    void shouldFindTransactionWithEntriesByReference() {
        Optional<LedgerTransaction> result = transactionRepository.findWithEntriesByTransactionReference("TXN-LOCK-100");

        assertThat(result).isPresent();
        assertThat(result.get().getTransactionType()).isEqualTo(TransactionType.BOUNTY_ESCROW_LOCK);
        assertThat(result.get().getEntries()).hasSize(2);
        assertThat(result.get().calculateTotalDebit()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.get().calculateTotalCredit()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("Should find transactions by bounty ID ordered by creation date descending")
    void shouldFindByBountyId() {
        List<LedgerTransaction> txns = transactionRepository.findByBountyIdOrderByCreatedAtDesc(sampleBountyId);

        assertThat(txns).hasSize(2);
        assertThat(txns.stream().map(LedgerTransaction::getTransactionReference))
                .containsExactly("TXN-REL-200", "TXN-LOCK-100");
    }

    @Test
    @DisplayName("Should find transactions with pagination")
    void shouldFindAllPaginated() {
        Page<LedgerTransaction> page = transactionRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("Should aggregate account balances across chart of accounts correctly")
    void shouldAggregateAccountBalances() {
        List<AccountBalanceProjection> balances = entryRepository.aggregateAccountBalances();

        assertThat(balances).isNotEmpty();

        for (AccountBalanceProjection b : balances) {
            if (b.getAccount() == LedgerAccount.PLATFORM_CASH) {
                // Debit: 1000, Credit: 0 -> Net Asset Balance = 1000
                assertThat(b.getTotalDebit()).isEqualByComparingTo(new BigDecimal("1000.00"));
                assertThat(b.getTotalCredit()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(b.getNetBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
            } else if (b.getAccount() == LedgerAccount.CLIENT_ESCROW_LOCKED) {
                // Credit: 1000, Debit: 500 -> Net Liability Balance = 500
                assertThat(b.getTotalCredit()).isEqualByComparingTo(new BigDecimal("1000.00"));
                assertThat(b.getTotalDebit()).isEqualByComparingTo(new BigDecimal("500.00"));
                assertThat(b.getNetBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
            } else if (b.getAccount() == LedgerAccount.DEVELOPER_PAYABLE) {
                // Credit: 425 -> Net Liability Balance = 425
                assertThat(b.getTotalCredit()).isEqualByComparingTo(new BigDecimal("425.00"));
                assertThat(b.getNetBalance()).isEqualByComparingTo(new BigDecimal("425.00"));
            } else if (b.getAccount() == LedgerAccount.PLATFORM_FEE_REVENUE) {
                // Credit: 75 -> Net Revenue Balance = 75
                assertThat(b.getTotalCredit()).isEqualByComparingTo(new BigDecimal("75.00"));
                assertThat(b.getNetBalance()).isEqualByComparingTo(new BigDecimal("75.00"));
            }
        }
    }

    @Test
    @DisplayName("Should calculate exact remaining locked escrow for a bounty")
    void shouldCalculateLockedEscrowForBounty() {
        // Locked: $1000 credit, Released: $500 debit -> Remaining locked escrow = $500
        BigDecimal lockedEscrow = entryRepository.getLockedEscrowForBounty(sampleBountyId);

        assertThat(lockedEscrow).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Should calculate total developer payable for a user")
    void shouldCalculateDeveloperPayableForUser() {
        BigDecimal payable = entryRepository.getPayableBalanceForUser(sampleUserId);

        assertThat(payable).isEqualByComparingTo(new BigDecimal("425.00"));
    }

    @Test
    @DisplayName("Should return 0 locked escrow for a non-existent bounty")
    void shouldReturnZeroEscrowForNonExistentBounty() {
        BigDecimal lockedEscrow = entryRepository.getLockedEscrowForBounty(9999L);

        assertThat(lockedEscrow).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
