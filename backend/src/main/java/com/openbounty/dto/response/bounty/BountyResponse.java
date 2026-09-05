package com.openbounty.dto.response.bounty;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.openbounty.dto.response.auth.UserSummaryResponse;
import com.openbounty.enums.BountyCategory;
import com.openbounty.enums.BountyStatus;
import com.openbounty.model.Bounty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Detailed representation of a Bounty challenge, including description, creator, and assigned developer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Detailed bounty challenge information")
public class BountyResponse {

    @Schema(description = "Bounty unique ID", example = "101")
    private Long id;

    @Schema(description = "Challenge title", example = "Build Spring Security 6 JWT Stateless Auth Engine")
    private String title;

    @Schema(description = "Full markdown/text description and acceptance criteria", example = "Implement a full stateless JWT authentication engine...")
    private String description;

    @Schema(description = "Domain category", example = "BACKEND_API")
    private BountyCategory category;

    @Schema(description = "Total escrow reward amount in USD", example = "1500.00")
    private BigDecimal rewardAmount;

    @Schema(description = "Current lifecycle status", example = "OPEN")
    private BountyStatus status;

    @Schema(description = "Target completion deadline", example = "2026-09-30")
    private LocalDate deadline;

    @Schema(description = "Client organization or individual who posted the bounty")
    private UserSummaryResponse client;

    @Schema(description = "Developer assigned to deliver the challenge (null if unassigned)")
    private UserSummaryResponse assignedDeveloper;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @Schema(description = "Creation timestamp", example = "2026-08-31T21:10:00.000Z")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @Schema(description = "Last update timestamp", example = "2026-08-31T21:10:00.000Z")
    private LocalDateTime updatedAt;

    public static BountyResponse from(Bounty bounty) {
        if (bounty == null) {
            return null;
        }
        return BountyResponse.builder()
                .id(bounty.getId())
                .title(bounty.getTitle())
                .description(bounty.getDescription())
                .category(bounty.getCategory())
                .rewardAmount(bounty.getRewardAmount())
                .status(bounty.getStatus())
                .deadline(bounty.getDeadline())
                .client(UserSummaryResponse.from(bounty.getClient()))
                .assignedDeveloper(UserSummaryResponse.from(bounty.getAssignedDeveloper()))
                .createdAt(bounty.getCreatedAt())
                .updatedAt(bounty.getUpdatedAt())
                .build();
    }
}
