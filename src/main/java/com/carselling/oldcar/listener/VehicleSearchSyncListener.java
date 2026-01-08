package com.carselling.oldcar.listener;

import com.carselling.oldcar.service.AdvancedSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event listener for synchronizing vehicle changes with Elasticsearch
 */
@Component
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class VehicleSearchSyncListener {

    private final AdvancedSearchService advancedSearchService;

    /**
     * Event for vehicle creation/update
     */
    public static class VehicleIndexEvent {
        private final Long vehicleId;
        private final String action; // "INDEX", "UPDATE", "DELETE"

        public VehicleIndexEvent(Long vehicleId, String action) {
            this.vehicleId = vehicleId;
            this.action = action;
        }

        public Long getVehicleId() {
            return vehicleId;
        }

        public String getAction() {
            return action;
        }
    }

    /**
     * Handle vehicle indexing after transaction commits
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleVehicleIndexEvent(VehicleIndexEvent event) {
        try {
            switch (event.getAction()) {
                case "INDEX", "UPDATE" -> {
                    log.debug("Syncing vehicle {} to Elasticsearch", event.getVehicleId());
                    advancedSearchService.syncVehicleToElasticsearch(event.getVehicleId());
                }
                case "DELETE" -> {
                    log.debug("Removing vehicle {} from Elasticsearch", event.getVehicleId());
                    advancedSearchService.removeVehicleFromElasticsearch(event.getVehicleId());
                }
                default -> log.warn("Unknown action {} for vehicle {}", event.getAction(), event.getVehicleId());
            }
        } catch (Exception e) {
            log.error("Error syncing vehicle {} with Elasticsearch: {}",
                    event.getVehicleId(), e.getMessage(), e);
            // Consider adding retry mechanism or dead letter queue here
        }
    }

    /**
     * Event for bulk synchronization
     */
    public static class BulkVehicleSyncEvent {
        private final String reason;

        public BulkVehicleSyncEvent(String reason) {
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * Handle bulk synchronization events
     */
    @Async
    @EventListener
    public void handleBulkVehicleSyncEvent(BulkVehicleSyncEvent event) {
        try {
            log.info("Starting bulk vehicle synchronization: {}", event.getReason());
            advancedSearchService.bulkSyncVehiclesToElasticsearch();
            log.info("Bulk vehicle synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error in bulk vehicle synchronization: {}", e.getMessage(), e);
        }
    }
}
