package com.openbounty.repository.projection;

import com.openbounty.enums.LedgerAccount;

import java.math.BigDecimal;

/**
 * Projection representing aggregated ledger debit and credit totals for a chart of accounts entry.
 */
public interface AccountBalanceProjection {

    LedgerAccount getAccount();

    BigDecimal getTotalDebit();

    BigDecimal getTotalCredit();

    /**
     * Net balance calculation based on account nature.
     * Assets (PLATFORM_CASH) increase on debit (Debit - Credit).
     * Liabilities & Revenues (CLIENT_ESCROW_LOCKED, DEVELOPER_PAYABLE, PLATFORM_FEE_REVENUE) increase on credit (Credit - Debit).
     */
    default BigDecimal getNetBalance() {
        BigDecimal debit = getTotalDebit() != null ? getTotalDebit() : BigDecimal.ZERO;
        BigDecimal credit = getTotalCredit() != null ? getTotalCredit() : BigDecimal.ZERO;

        if (getAccount() == LedgerAccount.PLATFORM_CASH) {
            return debit.subtract(credit);
        } else {
            return credit.subtract(debit);
        }
    }
}
