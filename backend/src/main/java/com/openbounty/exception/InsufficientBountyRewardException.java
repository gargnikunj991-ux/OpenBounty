package com.openbounty.exception;

import java.math.BigDecimal;

public class InsufficientBountyRewardException extends RuntimeException {

    public InsufficientBountyRewardException(String message) {
        super(message);
    }

    public InsufficientBountyRewardException(BigDecimal offeredReward, BigDecimal estimatedValue, double minPercentage) {
        super(String.format(
            "Offered reward of %s is below the platform minimum threshold of %.0f%% for an estimated project value of %s (minimum acceptable: %s)",
            offeredReward,
            minPercentage * 100,
            estimatedValue,
            estimatedValue.multiply(BigDecimal.valueOf(minPercentage))
        ));
    }
}
