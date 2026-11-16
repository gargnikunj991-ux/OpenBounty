package com.openbounty.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring task scheduling for background workers (e.g. escrow inactivity auto-release,
 * expired session cleanup).
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
