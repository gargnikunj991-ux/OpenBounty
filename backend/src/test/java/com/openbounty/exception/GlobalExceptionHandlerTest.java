package com.openbounty.exception;

import com.openbounty.dto.response.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/bounties/1");
    }

    @Test
    @DisplayName("handleResourceNotFound returns 404 with RFC 7807 details")
    void testHandleResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Bounty", "id", 1L);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleResourceNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getTitle()).isEqualTo("Resource Not Found");
        assertThat(response.getBody().getDetail()).contains("Bounty not found with id: '1'");
        assertThat(response.getBody().getInstance()).isEqualTo("/api/bounties/1");
    }

    @Test
    @DisplayName("handleInsufficientReward returns 400 with fair pricing explanation")
    void testHandleInsufficientReward() {
        InsufficientBountyRewardException ex = new InsufficientBountyRewardException(
                new BigDecimal("100.00"), new BigDecimal("10000.00"), 0.70);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInsufficientReward(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getTitle()).isEqualTo("Insufficient Bounty Reward");
        assertThat(response.getBody().getDetail()).contains("below the platform minimum threshold of 70%");
    }

    @Test
    @DisplayName("handleInsufficientBalance returns 400 when account balance is low")
    void testHandleInsufficientBalance() {
        InsufficientBalanceException ex = new InsufficientBalanceException(new BigDecimal("5000.00"), new BigDecimal("2000.00"));
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInsufficientBalance(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Insufficient Balance");
        assertThat(response.getBody().getDetail()).contains("Required balance of 5000.00 exceeds available balance of 2000.00");
    }

    @Test
    @DisplayName("handlePaymentProcessing returns 402 with transaction error")
    void testHandlePaymentProcessing() {
        PaymentProcessingException ex = new PaymentProcessingException("Card declined: Insufficient funds", "txn_12345", "insufficient_funds");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handlePaymentProcessing(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(402);
        assertThat(response.getBody().getTitle()).isEqualTo("Payment Processing Failed");
        assertThat(response.getBody().getDetail()).isEqualTo("Card declined: Insufficient funds");
    }

    @Test
    @DisplayName("handleSelfBidding returns 400 when client bids on own bounty")
    void testHandleSelfBidding() {
        SelfBiddingNotAllowedException ex = new SelfBiddingNotAllowedException("Clients cannot submit proposals to their own bounties.");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleSelfBidding(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Self-Dealing Forbidden");
    }

    @Test
    @DisplayName("handleMilestoneOrderViolation returns 400 for out-of-order sequence")
    void testHandleMilestoneOrderViolation() {
        MilestoneOrderViolationException ex = new MilestoneOrderViolationException("Milestone 1 must be approved before Milestone 2 can be submitted.");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleMilestoneOrderViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Milestone Order Violation");
    }

    @Test
    @DisplayName("handleDuplicateResource returns 409 CONFLICT")
    void testHandleDuplicateResource() {
        DuplicateResourceException ex = new DuplicateResourceException("User", "email", "alice@test.com");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleDuplicateResource(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getTitle()).isEqualTo("Resource Conflict");
    }

    @Test
    @DisplayName("handleInvalidStateTransition returns 409 CONFLICT")
    void testHandleInvalidStateTransition() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException("Bounty", "COMPLETED", "CANCELLED");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidStateTransition(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Invalid State Transition");
        assertThat(response.getBody().getDetail()).contains("Cannot transition Bounty from state 'COMPLETED' to 'CANCELLED'");
    }

    @Test
    @DisplayName("handleBountyExpired returns 410 GONE")
    void testHandleBountyExpired() {
        BountyExpiredException ex = new BountyExpiredException("Bounty deadline has expired and cannot accept proposals.");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleBountyExpired(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(410);
        assertThat(response.getBody().getTitle()).isEqualTo("Bounty Expired");
    }

    @Test
    @DisplayName("handleAccessDenied returns 403 FORBIDDEN")
    void testHandleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("You do not have permission to modify this bounty.");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("handleAccountSuspended returns 403 FORBIDDEN")
    void testHandleAccountSuspended() {
        AccountSuspendedException ex = new AccountSuspendedException("Account has been suspended due to platform violations.");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccountSuspended(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getTitle()).isEqualTo("Account Suspended");
    }

    @Test
    @DisplayName("handleValidationExceptions maps field errors accurately")
    void testHandleValidationExceptions() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError fieldError1 = new FieldError("bountyCreateRequest", "title", "Title cannot be blank");
        FieldError fieldError2 = new FieldError("bountyCreateRequest", "rewardAmount", "Reward must be greater than zero");

        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleValidationExceptions(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Validation Failed");
        assertThat(response.getBody().getErrors()).containsEntry("title", "Title cannot be blank");
        assertThat(response.getBody().getErrors()).containsEntry("rewardAmount", "Reward must be greater than zero");
    }

    @Test
    @DisplayName("handleGlobalException returns 500 without leaking stack traces")
    void testHandleGlobalException() {
        Exception ex = new NullPointerException("Unexpected null reference");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGlobalException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getTitle()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().getDetail()).doesNotContain("NullPointerException");
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred. Please contact support.");
    }
}
