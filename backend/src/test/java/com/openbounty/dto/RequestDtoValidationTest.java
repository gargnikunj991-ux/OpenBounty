package com.openbounty.dto;

import com.openbounty.dto.request.auth.LoginRequest;
import com.openbounty.dto.request.auth.RegisterRequest;
import com.openbounty.dto.request.bounty.BountyCreateRequest;
import com.openbounty.dto.request.milestone.MilestoneCreateRequest;
import com.openbounty.dto.request.milestone.MilestoneSubmitRequest;
import com.openbounty.dto.request.proposal.ProposalCreateRequest;
import com.openbounty.dto.request.review.ReviewCreateRequest;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RequestDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setupValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    @DisplayName("RegisterRequest Validation Tests")
    class RegisterRequestTests {

        @Test
        @DisplayName("Valid RegisterRequest produces no violations")
        void testValidRegisterRequest() {
            RegisterRequest request = RegisterRequest.builder()
                    .name("Alex Johnson")
                    .email("alex.johnson@example.com")
                    .password("SecurePassword123!")
                    .role(Role.ROLE_DEVELOPER)
                    .build();

            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Invalid email format and blank fields produce validation errors")
        void testInvalidRegisterRequest() {
            RegisterRequest request = RegisterRequest.builder()
                    .name("")
                    .email("not-an-email")
                    .password("short")
                    .role(null)
                    .build();

            Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("role"));
        }
    }

    @Nested
    @DisplayName("LoginRequest Validation Tests")
    class LoginRequestTests {

        @Test
        @DisplayName("Valid LoginRequest produces no violations")
        void testValidLoginRequest() {
            LoginRequest request = LoginRequest.builder()
                    .email("alex@example.com")
                    .password("password123")
                    .build();

            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Blank credentials produce validation violations")
        void testBlankLoginRequest() {
            LoginRequest request = LoginRequest.builder()
                    .email("")
                    .password("")
                    .build();

            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("BountyCreateRequest Validation Tests")
    class BountyCreateRequestTests {

        @Test
        @DisplayName("Valid BountyCreateRequest produces no violations")
        void testValidBountyCreateRequest() {
            BountyCreateRequest request = BountyCreateRequest.builder()
                    .title("Build Spring Security JWT Auth")
                    .description("Implement stateless JWT token authentication with RBAC and Swagger documentation.")
                    .category(BountyCategory.BACKEND_API)
                    .rewardAmount(new BigDecimal("1500.00"))
                    .deadline(LocalDate.now().plusMonths(1))
                    .build();

            Set<ConstraintViolation<BountyCreateRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Past deadline and negative reward amount produce violations")
        void testInvalidBountyCreateRequest() {
            BountyCreateRequest request = BountyCreateRequest.builder()
                    .title("Tiny")
                    .description("Short")
                    .category(null)
                    .rewardAmount(new BigDecimal("-50.00"))
                    .deadline(LocalDate.now().minusDays(5))
                    .build();

            Set<ConstraintViolation<BountyCreateRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("title"));
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("description"));
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("category"));
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rewardAmount"));
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("deadline"));
        }
    }

    @Nested
    @DisplayName("ProposalCreateRequest Validation Tests")
    class ProposalCreateRequestTests {

        @Test
        @DisplayName("Valid ProposalCreateRequest with nested milestones produces no violations")
        void testValidProposalCreateRequest() {
            MilestoneCreateRequest m1 = MilestoneCreateRequest.builder()
                    .title("Milestone 1: Security Setup")
                    .description("Implement JWT filter chain and user details service")
                    .build();

            ProposalCreateRequest request = ProposalCreateRequest.builder()
                    .approachDescription("I will implement a robust stateless JWT filter chain with comprehensive unit tests.")
                    .proposedAmount(new BigDecimal("1400.00"))
                    .estimatedDays(7)
                    .milestones(List.of(m1))
                    .build();

            Set<ConstraintViolation<ProposalCreateRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Empty milestone list and zero estimated days produce violations")
        void testInvalidProposalCreateRequest() {
            ProposalCreateRequest request = ProposalCreateRequest.builder()
                    .approachDescription("Too short")
                    .proposedAmount(new BigDecimal("0.00"))
                    .estimatedDays(0)
                    .milestones(Collections.emptyList())
                    .build();

            Set<ConstraintViolation<ProposalCreateRequest>> violations = validator.validate(request);
            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("approachDescription"));
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("proposedAmount"));
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("estimatedDays"));
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("milestones"));
        }
    }

    @Nested
    @DisplayName("MilestoneSubmitRequest Validation Tests")
    class MilestoneSubmitRequestTests {

        @Test
        @DisplayName("Valid submission URL produces no violations")
        void testValidMilestoneSubmitRequest() {
            MilestoneSubmitRequest request = MilestoneSubmitRequest.builder()
                    .deliverableUrl("https://github.com/developer/repo/pull/1")
                    .notes("All unit tests passing")
                    .build();

            Set<ConstraintViolation<MilestoneSubmitRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Invalid non-HTTP URL produces violation")
        void testInvalidDeliverableUrl() {
            MilestoneSubmitRequest request = MilestoneSubmitRequest.builder()
                    .deliverableUrl("ftp://invalid-url.com")
                    .notes("Submission notes")
                    .build();

            Set<ConstraintViolation<MilestoneSubmitRequest>> violations = validator.validate(request);
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("deliverableUrl");
        }
    }

    @Nested
    @DisplayName("ReviewCreateRequest Validation Tests")
    class ReviewCreateRequestTests {

        @Test
        @DisplayName("Valid review with rating 1-5 produces no violations")
        void testValidReviewCreateRequest() {
            ReviewCreateRequest request = ReviewCreateRequest.builder()
                    .bountyId(101L)
                    .revieweeId(1L)
                    .rating(5)
                    .feedback("Exceptional delivery!")
                    .build();

            Set<ConstraintViolation<ReviewCreateRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Rating out of 1-5 range produces violation")
        void testInvalidRatingRange() {
            ReviewCreateRequest request = ReviewCreateRequest.builder()
                    .bountyId(101L)
                    .revieweeId(1L)
                    .rating(6)
                    .feedback("Great")
                    .build();

            Set<ConstraintViolation<ReviewCreateRequest>> violations = validator.validate(request);
            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("rating");
        }
    }
}
