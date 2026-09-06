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
 * Compact summary representation of a Bounty challenge used in list and search endpoints.
 * Optimizes payload size for high-throughput pagination.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Compact bounty card representation for marketplace feeds")
public class BountySummaryResponse {

    @Schema(description = "Bounty unique ID", example = "101")
    private Long id;

    @Schema(description = "Challenge title", example = "Build Spring Security 6 JWT Stateless Auth Engine")
    private String title;

    @Schema(description = "Domain category", example = "BACKEND_API")
    private BountyCategory category;

    @Schema(description = "Total reward amount in USD", example = "1500.00")
    private BigDecimal rewardAmount;

    @Schema(description = "Current lifecycle status", example = "OPEN")
    private BountyStatus status;

    @Schema(description = "Target completion deadline", example = "2026-09-30")
    private LocalDate deadline;

    @Schema(description = "Client who posted the bounty")
    private UserSummaryResponse client;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    @Schema(description = "Creation timestamp", example = "2026-08-31T21:10:00.000Z")
    private LocalDateTime createdAt;

    public static BountySummaryResponse from(Bounty bounty) {
        if (bounty == null) {
            return null;
        }
        return BountySummaryResponse.builder()
                .id(bounty.getId())
                .title(bounty.getTitle())
                .category(bounty.getCategory())
                .rewardAmount(bounty.getRewardAmount())
                .status(bounty.getStatus())
                .deadline(bounty.getDeadline())
                .client(UserSummaryResponse.from(bounty.getClient()))
                .createdAt(bounty.getCreatedAt())
                .build();
    }
}
