package com.openbounty.exception;

import com.openbounty.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handling interceptor returning RFC 7807 Problem Details responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String BASE_ERROR_URI = "https://api.openbounty.dev/errors/";

    // =========================================================================
    // 404 NOT FOUND Handlers
    // =========================================================================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "resource-not-found", "Resource Not Found", ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("Path not found: {}", request.getRequestURI());
        return buildResponse(HttpStatus.NOT_FOUND, "endpoint-not-found", "Endpoint Not Found", "The requested path does not exist", request);
    }

    // =========================================================================
    // 400 BAD REQUEST Handlers
    // =========================================================================

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "bad-request", "Bad Request", ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientBountyRewardException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientReward(InsufficientBountyRewardException ex, HttpServletRequest request) {
        log.warn("Insufficient reward amount: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "insufficient-reward", "Insufficient Bounty Reward", ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex, HttpServletRequest request) {
        log.warn("Insufficient balance: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "insufficient-balance", "Insufficient Balance", ex.getMessage(), request);
    }

    @ExceptionHandler(SelfBiddingNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleSelfBidding(SelfBiddingNotAllowedException ex, HttpServletRequest request) {
        log.warn("Self-bidding attempt: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "self-bidding", "Self-Dealing Forbidden", ex.getMessage(), request);
    }

    @ExceptionHandler(MilestoneOrderViolationException.class)
    public ResponseEntity<ErrorResponse> handleMilestoneOrderViolation(MilestoneOrderViolationException ex, HttpServletRequest request) {
        log.warn("Milestone sequence violation: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "milestone-order-violation", "Milestone Order Violation", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidReviewException.class)
    public ResponseEntity<ErrorResponse> handleInvalidReview(InvalidReviewException ex, HttpServletRequest request) {
        log.warn("Invalid review submission: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "invalid-review", "Invalid Review", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        log.warn("Validation failed for request to {}: {}", request.getRequestURI(), errors);
        return buildResponseWithErrors(HttpStatus.BAD_REQUEST, "validation-error", "Validation Failed",
                "One or more request fields failed validation.", request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed HTTP message: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, "malformed-json", "Malformed JSON Request",
                "The request body is unreadable or contains invalid enum values", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        String detail = String.format("Parameter '%s' should be of type '%s'", ex.getName(), requiredType);
        log.warn("Type mismatch: {}", detail);
        return buildResponse(HttpStatus.BAD_REQUEST, "invalid-parameter-type", "Invalid Parameter Type", detail, request);
    }

    // =========================================================================
    // 401 UNAUTHORIZED Handlers
    // =========================================================================

    @ExceptionHandler({UnauthorizedException.class, InvalidTokenException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorized(RuntimeException ex, HttpServletRequest request) {
        log.warn("Unauthorized access attempt: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", ex.getMessage(), request);
    }

    // =========================================================================
    // 403 FORBIDDEN Handlers
    // =========================================================================

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied for URI {}: {}", request.getRequestURI(), ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "access-denied", "Access Denied", ex.getMessage(), request);
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<ErrorResponse> handleAccountSuspended(AccountSuspendedException ex, HttpServletRequest request) {
        log.warn("Suspended account access: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "account-suspended", "Account Suspended", ex.getMessage(), request);
    }

    // =========================================================================
    // 409 CONFLICT Handlers
    // =========================================================================

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(DuplicateResourceException ex, HttpServletRequest request) {
        log.warn("Duplicate resource conflict: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "resource-conflict", "Resource Conflict", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStateTransition(InvalidStateTransitionException ex, HttpServletRequest request) {
        log.warn("Invalid state transition: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "invalid-state-transition", "Invalid State Transition", ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Database integrity constraint violated: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "database-constraint-violation", "Database Constraint Violation",
                "Operation could not be completed due to a database integrity constraint", request);
    }

    // =========================================================================
    // 410 GONE Handlers
    // =========================================================================

    @ExceptionHandler(BountyExpiredException.class)
    public ResponseEntity<ErrorResponse> handleBountyExpired(BountyExpiredException ex, HttpServletRequest request) {
        log.warn("Bounty expired: {}", ex.getMessage());
        return buildResponse(HttpStatus.GONE, "bounty-expired", "Bounty Expired", ex.getMessage(), request);
    }

    // =========================================================================
    // 402 PAYMENT REQUIRED Handlers
    // =========================================================================

    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ErrorResponse> handlePaymentProcessing(PaymentProcessingException ex, HttpServletRequest request) {
        log.error("Payment processing error [TxId: {}, GatewayCode: {}]: {}",
                ex.getTransactionId(), ex.getGatewayErrorCode(), ex.getMessage());
        return buildResponse(HttpStatus.PAYMENT_REQUIRED, "payment-processing-failed", "Payment Processing Failed", ex.getMessage(), request);
    }

    // =========================================================================
    // 405 METHOD NOT ALLOWED Handlers
    // =========================================================================

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("HTTP method not supported: {} for {}", ex.getMethod(), request.getRequestURI());
        String detail = String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod());
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method Not Allowed", detail, request);
    }

    // =========================================================================
    // 500 INTERNAL SERVER ERROR Handler (Catch-all)
    // =========================================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at URI {}: ", request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "internal-server-error", "Internal Server Error",
                "An unexpected error occurred. Please contact support.", request);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String errorSlug, String title, String detail, HttpServletRequest request) {
        return buildResponseWithErrors(status, errorSlug, title, detail, request, null);
    }

    private ResponseEntity<ErrorResponse> buildResponseWithErrors(
            HttpStatus status, String errorSlug, String title, String detail, HttpServletRequest request, Map<String, String> errors) {

        ErrorResponse response = ErrorResponse.builder()
                .type(BASE_ERROR_URI + errorSlug)
                .title(title)
                .status(status.value())
                .detail(detail)
                .instance(request.getRequestURI())
                .timestamp(LocalDateTime.now())
                .errors(errors)
                .build();

        return ResponseEntity.status(status).body(response);
    }
}
