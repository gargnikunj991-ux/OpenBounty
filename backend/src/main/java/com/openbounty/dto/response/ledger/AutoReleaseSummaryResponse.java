package com.openbounty.dto.response.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Summary DTO reporting results of the inactivity escrow auto-release sweep.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoReleaseSummaryResponse {
    private int processedCount;
    private int thresholdDays;
    private List<Long> autoReleasedMilestoneIds;
    private String message;
    private LocalDateTime timestamp;
}
