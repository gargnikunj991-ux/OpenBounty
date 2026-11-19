package com.openbounty.dto.response.milestone;

import com.openbounty.enums.DisputeDecision;
import com.openbounty.enums.DisputeStatus;
import com.openbounty.model.MilestoneDispute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO representing an arbitration dispute record.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneDisputeResponse {
    private Long id;
    private Long milestoneId;
    private Long bountyId;
    private String bountyTitle;
    private Long initiatorId;
    private String initiatorName;
    private String reason;
    private String evidenceUrl;
    private DisputeStatus status;
    private DisputeDecision decision;
    private String resolutionNotes;
    private BigDecimal frozenAmount;
    private Long resolvedById;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;

    public static MilestoneDisputeResponse from(MilestoneDispute dispute) {
        if (dispute == null) return null;
        return MilestoneDisputeResponse.builder()
                .id(dispute.getId())
                .milestoneId(dispute.getMilestone() != null ? dispute.getMilestone().getId() : null)
                .bountyId(dispute.getBounty() != null ? dispute.getBounty().getId() : null)
                .bountyTitle(dispute.getBounty() != null ? dispute.getBounty().getTitle() : null)
                .initiatorId(dispute.getInitiator() != null ? dispute.getInitiator().getId() : null)
                .initiatorName(dispute.getInitiator() != null ? dispute.getInitiator().getName() : null)
                .reason(dispute.getReason())
                .evidenceUrl(dispute.getEvidenceUrl())
                .status(dispute.getStatus())
                .decision(dispute.getDecision())
                .resolutionNotes(dispute.getResolutionNotes())
                .frozenAmount(dispute.getFrozenAmount())
                .resolvedById(dispute.getResolvedBy() != null ? dispute.getResolvedBy().getId() : null)
                .resolvedAt(dispute.getResolvedAt())
                .createdAt(dispute.getCreatedAt())
                .build();
    }
}
