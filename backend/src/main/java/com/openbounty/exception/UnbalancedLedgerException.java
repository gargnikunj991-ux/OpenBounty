package com.openbounty.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when a double-entry ledger journal transaction violates the fundamental
 * accounting invariant: SUM(Debits) == SUM(Credits).
 */
public class UnbalancedLedgerException extends RuntimeException {

    private final BigDecimal totalDebit;
    private final BigDecimal totalCredit;
    private final BigDecimal discrepancy;

    public UnbalancedLedgerException(BigDecimal totalDebit, BigDecimal totalCredit) {
        super(String.format("Unbalanced ledger transaction: Total debits (%s) must exactly equal total credits (%s). Discrepancy: %s",
                totalDebit != null ? totalDebit.toPlainString() : "0.00",
                totalCredit != null ? totalCredit.toPlainString() : "0.00",
                totalDebit != null && totalCredit != null ? totalDebit.subtract(totalCredit).abs().toPlainString() : "unknown"));
        this.totalDebit = totalDebit;
        this.totalCredit = totalCredit;
        this.discrepancy = totalDebit != null && totalCredit != null ? totalDebit.subtract(totalCredit).abs() : BigDecimal.ZERO;
    }

    public UnbalancedLedgerException(String message) {
        super(message);
        this.totalDebit = BigDecimal.ZERO;
        this.totalCredit = BigDecimal.ZERO;
        this.discrepancy = BigDecimal.ZERO;
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    public BigDecimal getTotalCredit() {
        return totalCredit;
    }

    public BigDecimal getDiscrepancy() {
        return discrepancy;
    }
}
