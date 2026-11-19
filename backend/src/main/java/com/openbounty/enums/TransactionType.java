package com.openbounty.enums;

/**
 * High-level business operations resulting in a double-entry journal transaction.
 */
public enum TransactionType {
    /**
     * Client deposits bounty reward funds into escrow upon creation/publication.
     */
    BOUNTY_ESCROW_LOCK,

    /**
     * Client cancels bounty without accepted deliverable, returning escrow funds.
     */
    BOUNTY_ESCROW_REFUND,

    /**
     * Milestone deliverable approved, unlocking escrow into developer payable and platform fee.
     */
    MILESTONE_PAYOUT_RELEASE,

    /**
     * Developer withdraws accumulated balance to external bank/crypto account.
     */
    DEVELOPER_PAYOUT_SETTLEMENT,

    /**
     * Funds frozen and held in suspense pending dispute resolution.
     */
    DISPUTE_FREEZE,

    /**
     * Dispute settled by admin arbitrator, releasing escrow accordingly.
     */
    DISPUTE_RESOLUTION
}
