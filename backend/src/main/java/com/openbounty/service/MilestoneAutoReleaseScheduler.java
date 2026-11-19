package com.openbounty.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled background worker executing the 14-day inactivity escrow auto-release sweep.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "openbounty.escrow.inactivity-auto-release.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MilestoneAutoReleaseScheduler {

    private final MilestoneInactivityAutoReleaseService autoReleaseService;

    @Scheduled(cron = "${openbounty.escrow.inactivity-auto-release.cron:0 0 2 * * *}")
    public void executeInactivityAutoReleaseSweep() {
        log.info("Cron trigger: Starting scheduled 14-day inactivity escrow auto-release sweep.");
        try {
            autoReleaseService.sweepAndAutoRelease();
        } catch (Exception ex) {
            log.error("Error occurred during scheduled escrow inactivity auto-release: {}", ex.getMessage(), ex);
        }
    }
}
