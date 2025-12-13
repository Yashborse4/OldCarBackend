package com.carselling.oldcar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stub AdvancedSearchService for compilation
 * Full implementation is in the disabled folder
 */
@Slf4j
@Service
public class AdvancedSearchService {
    
    /**
     * Sync vehicle to Elasticsearch - stub implementation
     */
    public void syncVehicleToElasticsearch(Long vehicleId) {
        log.info("Elasticsearch sync stub: Vehicle {}", vehicleId);
    }
    
    /**
     * Remove vehicle from Elasticsearch - stub implementation
     */
    public void removeVehicleFromElasticsearch(Long vehicleId) {
        log.info("Elasticsearch remove stub: Vehicle {}", vehicleId);
    }
    
    /**
     * Bulk sync vehicles to Elasticsearch - stub implementation
     */
    public void bulkSyncVehiclesToElasticsearch() {
        log.info("Elasticsearch bulk sync stub: All vehicles");
    }
}
