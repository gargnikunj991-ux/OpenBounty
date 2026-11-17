package com.openbounty.enums;

/**
 * Administrator arbitration decision for resolving a milestone dispute.
 */
public enum DisputeDecision {
    /**
     * Settle dispute in favor of the client, refunding escrow.
     */
    REFUND_CLIENT,

    /**
     * Settle dispute in favor of the developer, disbursing milestone payout.
     */
    DISBURSE_DEVELOPER,

    /**
     * Settle dispute equally, splitting escrow 50/50 between client refund and developer payout.
     */
    SPLIT_50_50
}
