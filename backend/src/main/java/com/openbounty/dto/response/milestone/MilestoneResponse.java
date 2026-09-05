package com.openbounty.dto.response.milestone;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.openbounty.enums.MilestoneStatus;
import com.openbounty.model.Milestone;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Detailed representation of a deliverable milestone.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Milestone deliverable response")
public class MilestoneResponse {

    @Schema(description = "Milestone unique identifier", example = "301")
    private Long id;

    @Schema(description = "Milestone title", example = "Milestone 1: Filter Chain & JWT Token Generation")
    private String title;

    @Schema(description = "Milestone detailed deliverable scope", example = "Setup SecurityFilterChain and JwtService with RSA-256 / HMAC-SHA256 signature verification.")
    private String description;

    @Schema(description = "URL pointing to the submitted deliverable proof", example = "https://github.com/alex-dev/openbounty-security-module/pull/1")
    private String deliverableUrl;

    @Schema(description = "Current lifecycle status of this milestone", example = "PENDING")
    private MilestoneStatus status;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @Schema(description = "Submission timestamp", example = "2026-08-31T21:20:00.000Z")
    private LocalDateTime submittedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @Schema(description = "Approval timestamp", example = "2026-08-31T21:25:00.000Z")
    private LocalDateTime approvedAt;

    public static MilestoneResponse from(Milestone milestone) {
        if (milestone == null) {
            return null;
        }
        return MilestoneResponse.builder()
                .id(milestone.getId())
                .title(milestone.getTitle())
                .description(milestone.getDescription())
                .deliverableUrl(milestone.getDeliverableUrl())
                .status(milestone.getStatus())
                .submittedAt(milestone.getSubmittedAt())
                .approvedAt(milestone.getApprovedAt())
                .build();
    }
}
