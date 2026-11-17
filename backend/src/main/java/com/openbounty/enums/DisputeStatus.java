package com.openbounty.enums;

/**
 * Lifecycle states of an arbitration dispute.
 */
public enum DisputeStatus {
    /**
     * Dispute raised and awaiting administrator arbitration.
     */
    PENDING_REVIEW,

    /**
     * Dispute resolved by administrator with full refund to client.
     */
    RESOLVED_CLIENT_REFUND,

    /**
     * Dispute resolved by administrator with milestone payout to developer.
     */
    RESOLVED_DEVELOPER_PAYOUT,

    /**
     * Dispute resolved by administrator splitting escrow 50/50 between client and developer.
     */
    RESOLVED_SPLIT
}
