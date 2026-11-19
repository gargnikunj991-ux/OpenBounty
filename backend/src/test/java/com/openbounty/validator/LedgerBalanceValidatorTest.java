package com.openbounty.validator;

import com.openbounty.enums.EntryType;
import com.openbounty.enums.LedgerAccount;
import com.openbounty.enums.TransactionType;
import com.openbounty.exception.BadRequestException;
import com.openbounty.exception.UnbalancedLedgerException;
import com.openbounty.model.LedgerEntry;
import com.openbounty.model.LedgerTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LedgerBalanceValidator Unit Tests")
class LedgerBalanceValidatorTest {

    private LedgerBalanceValidator validator;

    @BeforeEach
    void setUp() {
        validator = new LedgerBalanceValidator();
    }

    @Test
    @DisplayName("Should pass when total debits exactly equal total credits (Simple 2-legged entry)")
    void shouldPassWhenDebitsEqualCreditsSimple() {
        LedgerTransaction txn = LedgerTransaction.builder()
                .transactionReference("TXN-TEST-001")
                .transactionType(TransactionType.BOUNTY_ESCROW_LOCK)
                .entries(new ArrayList<>())
                .build();

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.PLATFORM_CASH)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("1000.00"))
                .build());

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.CLIENT_ESCROW_LOCKED)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("1000.00"))
                .build());

        validator.validate(txn);
        assertThat(txn.isBalanced()).isTrue();
    }

    @Test
    @DisplayName("Should pass when single debit equals multiple split credits (Compound journal entry)")
    void shouldPassWhenSingleDebitEqualsMultipleSplitCredits() {
        LedgerTransaction txn = LedgerTransaction.builder()
                .transactionReference("TXN-TEST-002")
                .transactionType(TransactionType.MILESTONE_PAYOUT_RELEASE)
                .entries(new ArrayList<>())
                .build();

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.CLIENT_ESCROW_LOCKED)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("500.00"))
                .build());

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.DEVELOPER_PAYABLE)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("425.00"))
                .build());

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.PLATFORM_FEE_REVENUE)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("75.00"))
                .build());

        validator.validate(txn);
        assertThat(txn.isBalanced()).isTrue();
        assertThat(txn.calculateTotalDebit()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(txn.calculateTotalCredit()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("Should throw UnbalancedLedgerException when debit exceeds credit by even one cent")
    void shouldThrowWhenDebitExceedsCredit() {
        LedgerTransaction txn = LedgerTransaction.builder()
                .transactionReference("TXN-TEST-003")
                .transactionType(TransactionType.BOUNTY_ESCROW_LOCK)
                .entries(new ArrayList<>())
                .build();

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.PLATFORM_CASH)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("1000.01"))
                .build());

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.CLIENT_ESCROW_LOCKED)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("1000.00"))
                .build());

        assertThatThrownBy(() -> validator.validate(txn))
                .isInstanceOf(UnbalancedLedgerException.class)
                .hasMessageContaining("Discrepancy: 0.01");
    }

    @Test
    @DisplayName("Should throw BadRequestException when transaction is null")
    void shouldThrowWhenTransactionIsNull() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ledger transaction cannot be null");
    }

    @Test
    @DisplayName("Should throw BadRequestException when transaction has fewer than 2 entries")
    void shouldThrowWhenTransactionHasFewerThanTwoEntries() {
        LedgerTransaction txn = LedgerTransaction.builder()
                .transactionReference("TXN-TEST-004")
                .transactionType(TransactionType.BOUNTY_ESCROW_LOCK)
                .entries(new ArrayList<>())
                .build();

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.PLATFORM_CASH)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("100.00"))
                .build());

        assertThatThrownBy(() -> validator.validate(txn))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must contain at least 2 entries");
    }

    @Test
    @DisplayName("Should throw BadRequestException when entry has zero or negative amount")
    void shouldThrowWhenEntryHasNonPositiveAmount() {
        LedgerTransaction txn = LedgerTransaction.builder()
                .transactionReference("TXN-TEST-005")
                .transactionType(TransactionType.BOUNTY_ESCROW_LOCK)
                .entries(new ArrayList<>())
                .build();

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.PLATFORM_CASH)
                .entryType(EntryType.DEBIT)
                .amount(BigDecimal.ZERO)
                .build());

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.CLIENT_ESCROW_LOCKED)
                .entryType(EntryType.CREDIT)
                .amount(BigDecimal.ZERO)
                .build());

        assertThatThrownBy(() -> validator.validate(txn))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("amount must be strictly greater than zero");
    }

    @Test
    @DisplayName("Should throw BadRequestException when entry account is null")
    void shouldThrowWhenAccountIsNull() {
        LedgerTransaction txn = LedgerTransaction.builder()
                .transactionReference("TXN-TEST-006")
                .transactionType(TransactionType.BOUNTY_ESCROW_LOCK)
                .entries(new ArrayList<>())
                .build();

        txn.addEntry(LedgerEntry.builder()
                .account(null)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("100.00"))
                .build());

        txn.addEntry(LedgerEntry.builder()
                .account(LedgerAccount.CLIENT_ESCROW_LOCKED)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("100.00"))
                .build());

        assertThatThrownBy(() -> validator.validate(txn))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("account cannot be null");
    }
}
