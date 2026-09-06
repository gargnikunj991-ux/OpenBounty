package com.openbounty.exception;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String message) {
        super(message);
    }

    public InsufficientBalanceException(BigDecimal requiredAmount, BigDecimal availableBalance) {
        super(String.format("Required balance of %s exceeds available balance of %s", requiredAmount, availableBalance));
    }
}
