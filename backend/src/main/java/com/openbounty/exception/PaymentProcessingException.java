package com.openbounty.exception;

public class PaymentProcessingException extends RuntimeException {

    private final String transactionId;
    private final String gatewayErrorCode;

    public PaymentProcessingException(String message) {
        super(message);
        this.transactionId = null;
        this.gatewayErrorCode = null;
    }

    public PaymentProcessingException(String message, String transactionId, String gatewayErrorCode) {
        super(message);
        this.transactionId = transactionId;
        this.gatewayErrorCode = gatewayErrorCode;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getGatewayErrorCode() {
        return gatewayErrorCode;
    }
}
