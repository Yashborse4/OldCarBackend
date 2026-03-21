package com.carselling.oldcar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Monitor system health for adaptive load shedding.
 * Inspired by Netflix's Concurrency Limits.
 */
@Service
@Slf4j
public class SystemHealthMonitor {

    private final HealthEndpoint healthEndpoint;
    private final MetricsEndpoint metricsEndpoint;

    public SystemHealthMonitor(HealthEndpoint healthEndpoint, MetricsEndpoint metricsEndpoint) {
        this.healthEndpoint = healthEndpoint;
        this.metricsEndpoint = metricsEndpoint;
    }

    /**
     * Get system health score (0.0 to 1.0).
     * 1.0 = Perfect Health
     * 0.0 = Critical / Down
     */
    public double getHealthScore() {
        try {
            HealthComponent health = healthEndpoint.health();
            if (health.getStatus().equals(Status.DOWN) || health.getStatus().equals(Status.OUT_OF_SERVICE)) {
                return 0.0;
            }

            // Simple degradation model
            double score = 1.0;

            // Check CPU usage if available
            Double cpuUsage = getCpuUsage();
            if (cpuUsage != null) {
                if (cpuUsage > 0.90) score *= 0.5; // Critical load
                else if (cpuUsage > 0.70) score *= 0.8; // Heavy load
            }

            return score;
        } catch (Exception e) {
            log.error("Failed to calculate system health score", e);
            return 1.0; // Fail open
        }
    }

    private Double getCpuUsage() {
        try {
            var cpuMeter = metricsEndpoint.metric("system.cpu.usage", Collections.emptyList());
            if (cpuMeter != null && cpuMeter.getMeasurements() != null && !cpuMeter.getMeasurements().isEmpty()) {
                return cpuMeter.getMeasurements().get(0).getValue();
            }
        } catch (Exception e) {
            log.trace("Metric system.cpu.usage not available", e);
        }
        return null;
    }
}
