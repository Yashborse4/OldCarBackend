package com.carselling.oldcar.scheduler;

import com.carselling.oldcar.service.AdvancedSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class SearchIndexScheduler {

    private final AdvancedSearchService advancedSearchService;

    /**
     * Re-indexes all vehicles from the database to OpenSearch.
     * Scheduled to run every night at 2:00 AM Indian Standard Time (Asia/Kolkata).
     * 
     * Cron expression: "0 0 2 * * *"
     * Seconds=0, Minutes=0, Hours=2, DayOfMonth=*, Month=*, DayOfWeek=*
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Kolkata")
    public void scheduleReindexing() {
        log.info("Starting scheduled nightly re-indexing job at {}", LocalDateTime.now());
        try {
            advancedSearchService.bulkSyncVehiclesToElasticsearch();
            log.info("Scheduled nightly re-indexing job completed successfully.");
        } catch (Exception e) {
            log.error("Scheduled nightly re-indexing job failed.", e);
        }
    }
}
