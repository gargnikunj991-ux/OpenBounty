package com.openbounty.dto.request.proposal;

import com.openbounty.dto.request.milestone.MilestoneCreateRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request payload for developers submitting a technical solution proposal to a bounty.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Proposal submission payload")
public class ProposalCreateRequest {

    @NotBlank(message = "Approach description is required")
    @Size(min = 10, max = 5000, message = "Approach description must be between 10 and 5000 characters")
    @Schema(description = "Detailed technical solution approach and architecture", example = "I will implement a modular filter chain using JJWT library, with Redis blacklist support and JUnit integration tests.")
    private String approachDescription;

    @NotNull(message = "Proposed amount is required")
    @DecimalMin(value = "1.00", message = "Proposed amount must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Proposed amount must be a valid monetary amount with up to 2 decimal places")
    @Schema(description = "Total proposed bid price in USD", example = "1400.00")
    private BigDecimal proposedAmount;

    @NotNull(message = "Estimated days is required")
    @Min(value = 1, message = "Estimated days must be at least 1")
    @Max(value = 365, message = "Estimated days cannot exceed 365 days")
    @Schema(description = "Estimated number of calendar days to complete all deliverables", example = "7")
    private Integer estimatedDays;

    @NotEmpty(message = "At least one milestone deliverable is required")
    @Valid
    @Schema(description = "List of structured deliverable milestones")
    private List<MilestoneCreateRequest> milestones;
}
