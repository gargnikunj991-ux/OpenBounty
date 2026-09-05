package com.openbounty.dto.response.milestone;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openbounty.enums.BountyStatus;
import com.openbounty.enums.MilestoneStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Response payload confirming client approval of a milestone.
 * Communicates if all milestones have completed and triggered bounty completion.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Milestone approval response payload")
public class MilestoneApproveResponse {

    @Schema(description = "Approved milestone ID", example = "301")
    private Long id;

    @Schema(description = "Status of the approved milestone", example = "APPROVED")
    private MilestoneStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @Schema(description = "Timestamp when milestone was approved", example = "2026-08-31T21:25:00.000Z")
    private LocalDateTime approvedAt;

    @Schema(description = "Whether all milestones for the proposal are now approved", example = "true")
    private boolean allMilestonesApproved;

    @Schema(description = "Updated status of the associated bounty", example = "COMPLETED")
    private BountyStatus bountyStatus;

    @Schema(description = "Informative status message", example = "Milestone approved. All deliverables verified; bounty marked as COMPLETED.")
    private String message;
}
