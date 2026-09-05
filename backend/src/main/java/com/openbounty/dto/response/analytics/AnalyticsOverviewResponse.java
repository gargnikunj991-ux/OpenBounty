package com.openbounty.dto.response.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * High-level platform statistics and KPIs response payload.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Platform-wide analytics overview and metrics")
public class AnalyticsOverviewResponse {

    @Schema(description = "Total number of bounties created across all statuses", example = "250")
    private long totalBounties;

    @Schema(description = "Number of currently active bounties (OPEN, IN_REVIEW, ASSIGNED, IN_PROGRESS)", example = "84")
    private long activeBounties;

    @Schema(description = "Number of successfully completed bounties", example = "152")
    private long completedBounties;

    @Schema(description = "Total escrow funds disbursed to developers in USD", example = "385000.00")
    private BigDecimal totalFundsDisbursed;

    @Schema(description = "Total registered developers on the platform", example = "1200")
    private long totalDevelopers;

    @Schema(description = "Total registered client organizations on the platform", example = "340")
    private long totalClients;
}
