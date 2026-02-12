package com.carselling.oldcar.scheduler;

import com.carselling.oldcar.service.AdvancedSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class SearchIndexScheduler {

    private final AdvancedSearchService advancedSearchService;
    private final JobExecutionService jobExecutionService;

    /**
     * Re-indexes all vehicles from the database to OpenSearch.
     * Scheduled to run every night at 2:00 AM Indian Standard Time (Asia/Kolkata).
     * 
     * Cron expression: "0 0 2 * * *"
     * Seconds=0, Minutes=0, Hours=2, DayOfMonth=*, Month=*, DayOfWeek=*
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")
    public void scheduleReindexing() {
        jobExecutionService.executeWithMetrics("VehicleReindexing", () -> {
            // 1. Check Health
            if (!advancedSearchService.isIndexHealthy()) {
                throw new RuntimeException("Skipping re-indexing job: Elasticsearch cluster is unhealthy.");
            }

            // 2. Check for last successful run
            var lastRun = jobExecutionService.getLastSuccessfulJob("VehicleReindexing");

            int count;
            // 3. Decide: Incremental vs Full
            if (lastRun != null && lastRun.getEndTime() != null) {
                // Incremental
                log.info("Starting Incremental Indexing. Last run: {}", lastRun.getEndTime());
                count = advancedSearchService.incrementalSyncVehicles(lastRun.getEndTime());
            } else {
                // Full Blue-Green
                log.info("Starting Full Blue-Green Indexing (First run or previous failed).");
                count = advancedSearchService.bulkSyncVehiclesToElasticsearch();
            }

            return java.util.Map.of("recordsProcessed", count, "mode", lastRun != null ? "INCREMENTAL" : "FULL");
        });
    }
}
