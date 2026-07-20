package com.training.orderservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tuning knobs for the reconciliation sweep (SDD Section 31). The scheduling cadence
 * itself ({@code reconciliation.sweep.fixed-delay}) is read directly by the
 * {@code @Scheduled} annotation via a property placeholder, not through this record.
 */
@ConfigurationProperties(prefix = "reconciliation.sweep")
public record ReconciliationSweepProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("true") boolean dryRun,
        @DefaultValue("PT15M") Duration stuckThreshold,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("100") int batchSize) {
}