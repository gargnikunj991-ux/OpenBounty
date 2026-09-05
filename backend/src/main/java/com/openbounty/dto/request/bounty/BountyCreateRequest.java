package com.openbounty.dto.request.bounty;

import com.openbounty.enums.BountyCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload for creating a new bounty / technical challenge.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Bounty creation payload")
public class BountyCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
    @Schema(description = "Clear summary title for the challenge", example = "Build Spring Security 6 JWT Stateless Auth Engine")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 5000, message = "Description must be between 10 and 5000 characters")
    @Schema(description = "Detailed specifications and acceptance criteria", example = "Implement a full stateless JWT authentication engine with role-based access control, refresh tokens, and swagger integration.")
    private String description;

    @NotNull(message = "Category is required")
    @Schema(description = "Technical domain category", example = "BACKEND_API")
    private BountyCategory category;

    @NotNull(message = "Reward amount is required")
    @DecimalMin(value = "1.00", message = "Reward amount must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Reward amount must be a valid monetary amount with up to 2 decimal places")
    @Schema(description = "Total escrow reward amount in USD", example = "1500.00")
    private BigDecimal rewardAmount;

    @NotNull(message = "Deadline is required")
    @Future(message = "Deadline must be a future date")
    @Schema(description = "Target completion deadline (YYYY-MM-DD)", example = "2026-12-31")
    private LocalDate deadline;
}
