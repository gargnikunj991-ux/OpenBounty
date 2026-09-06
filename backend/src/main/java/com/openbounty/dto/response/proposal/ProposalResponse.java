package com.openbounty.dto.response.proposal;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.openbounty.dto.response.auth.UserSummaryResponse;
import com.openbounty.dto.response.milestone.MilestoneResponse;
import com.openbounty.enums.ProposalStatus;
import com.openbounty.model.Proposal;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Detailed representation of a developer solution proposal submitted for a bounty.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Developer solution proposal response")
public class ProposalResponse {

    @Schema(description = "Proposal unique identifier", example = "201")
    private Long id;

    @Schema(description = "Associated bounty ID", example = "101")
    private Long bountyId;

    @Schema(description = "Developer who submitted the proposal")
    private UserSummaryResponse developer;

    @Schema(description = "Technical approach description", example = "I will implement a modular filter chain using JJWT library...")
    private String approachDescription;

    @Schema(description = "Proposed bid price in USD", example = "1400.00")
    private BigDecimal proposedAmount;

    @Schema(description = "Estimated days to completion", example = "7")
    private Integer estimatedDays;

    @Schema(description = "Current proposal evaluation status", example = "PENDING")
    private ProposalStatus status;

    @Schema(description = "Structured deliverable milestones")
    private List<MilestoneResponse> milestones;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @Schema(description = "Proposal submission timestamp", example = "2026-08-31T21:15:00.000Z")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @Schema(description = "Last update timestamp", example = "2026-08-31T21:15:00.000Z")
    private LocalDateTime updatedAt;

    public static ProposalResponse from(Proposal proposal) {
        if (proposal == null) {
            return null;
        }
        List<MilestoneResponse> milestoneResponses = proposal.getMilestones() != null
                ? proposal.getMilestones().stream().map(MilestoneResponse::from).toList()
                : Collections.emptyList();

        return ProposalResponse.builder()
                .id(proposal.getId())
                .bountyId(proposal.getBounty() != null ? proposal.getBounty().getId() : null)
                .developer(UserSummaryResponse.from(proposal.getDeveloper()))
                .approachDescription(proposal.getApproachDescription())
                .proposedAmount(proposal.getProposedAmount())
                .estimatedDays(proposal.getEstimatedDays())
                .status(proposal.getStatus())
                .milestones(milestoneResponses)
                .createdAt(proposal.getCreatedAt())
                .updatedAt(proposal.getUpdatedAt())
                .build();
    }
}
