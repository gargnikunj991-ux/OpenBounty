package com.openbounty.enums;

/**
 * Standard chart of accounts for OpenBounty's double-entry financial ledger.
 *
 * Accounting Equation: Assets = Liabilities + Equity
 * - Assets increase on DEBIT, decrease on CREDIT.
 * - Liabilities and Revenues increase on CREDIT, decrease on DEBIT.
 */
public enum LedgerAccount {
    /**
     * Asset: Liquid fiat/cash held in platform omnibus custody accounts (e.g. Stripe balance or platform bank).
     */
    PLATFORM_CASH,

    /**
     * Liability: Funds deposited by clients, locked in escrow pending deliverable completion.
     */
    CLIENT_ESCROW_LOCKED,

    /**
     * Liability: Approved funds earned by and payable to developers awaiting payout/withdrawal.
     */
    DEVELOPER_PAYABLE,

    /**
     * Revenue: Platform take-rate commission fee recognized upon milestone approval.
     */
    PLATFORM_FEE_REVENUE,

    /**
     * Liability/Suspense: Funds frozen under active arbitration or dispute review.
     */
    DISPUTE_SUSPENSE
}
