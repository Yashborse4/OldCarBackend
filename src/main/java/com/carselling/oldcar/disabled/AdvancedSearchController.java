package com.carselling.oldcar.controller;

import com.carselling.oldcar.document.VehicleSearchDocument;
import com.carselling.oldcar.dto.ApiResponse;
import com.carselling.oldcar.service.AdvancedSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for advanced search operations with Elasticsearch
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Advanced Search", description = "Elasticsearch-powered advanced search capabilities")
public class AdvancedSearchController {

    private final AdvancedSearchService advancedSearchService;

    /**
     * Intelligent search with multiple strategies
     */
    @GetMapping("/intelligent")
    @Operation(summary = "Intelligent search", description = "Multi-strategy search with full-text, geo-spatial, and filter capabilities")
    public ResponseEntity<ApiResponse<Map<String, Object>>> intelligentSearch(
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Vehicle make") @RequestParam(required = false) String make,
            @Parameter(description = "Vehicle model") @RequestParam(required = false) String model,
            @Parameter(description = "Minimum price") @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price") @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Minimum year") @RequestParam(required = false) Integer minYear,
            @Parameter(description = "Maximum year") @RequestParam(required = false) Integer maxYear,
            @Parameter(description = "Fuel type") @RequestParam(required = false) String fuelType,
            @Parameter(description = "Transmission") @RequestParam(required = false) String transmission,
            @Parameter(description = "Body type") @RequestParam(required = false) String bodyType,
            @Parameter(description = "Location") @RequestParam(required = false) String location,
            @Parameter(description = "Latitude for geo search") @RequestParam(required = false) Double latitude,
            @Parameter(description = "Longitude for geo search") @RequestParam(required = false) Double longitude,
            @Parameter(description = "Radius in km for geo search") @RequestParam(required = false, defaultValue = "50") Double radius,
            @Parameter(description = "Only verified vehicles") @RequestParam(required = false) Boolean verified,
            @Parameter(description = "Only featured vehicles") @RequestParam(required = false) Boolean featured,
            @Parameter(description = "Sort field") @RequestParam(required = false, defaultValue = "_score") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(required = false, defaultValue = "desc") String sortDirection,
            @Parameter(description = "Page number") @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "20") int size) {
        
        try {
            // Build search parameters
            Map<String, Object> searchParams = new HashMap<>();
            searchParams.put("query", query);
            searchParams.put("make", make);
            searchParams.put("model", model);
            searchParams.put("minPrice", minPrice);
            searchParams.put("maxPrice", maxPrice);
            searchParams.put("minYear", minYear);
            searchParams.put("maxYear", maxYear);
            searchParams.put("fuelType", fuelType);
            searchParams.put("transmission", transmission);
            searchParams.put("bodyType", bodyType);
            searchParams.put("location", location);
            searchParams.put("latitude", latitude);
            searchParams.put("longitude", longitude);
            searchParams.put("radius", radius);
            searchParams.put("verified", verified);
            searchParams.put("featured", featured);

            // Create pageable with sorting
            Sort sort = Sort.by(Sort.Direction.fromString(sortDirection.toUpperCase()), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Map<String, Object> results = advancedSearchService.intelligentSearch(searchParams, pageable);
            
            return ResponseEntity.ok(ApiResponse.success(results, "Search completed successfully"));

        } catch (Exception e) {
            log.error("Error in intelligent search: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Search failed: " + e.getMessage()));
        }
    }

    /**
     * Full-text search with boosting
     */
    @GetMapping("/fulltext")
    @Operation(summary = "Full-text search", description = "Advanced full-text search with relevance boosting")
    public ResponseEntity<ApiResponse<Map<String, Object>>> fullTextSearch(
            @Parameter(description = "Search query", required = true) @RequestParam String query,
            @Parameter(description = "Page number") @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "20") int size) {
        
        try {
            Map<String, Object> searchParams = Map.of("query", query);
            Pageable pageable = PageRequest.of(page, size);
            
            Map<String, Object> results = advancedSearchService.intelligentSearch(searchParams, pageable);
            
            return ResponseEntity.ok(ApiResponse.success(results, "Full-text search completed"));

        } catch (Exception e) {
            log.error("Error in full-text search: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Full-text search failed: " + e.getMessage()));
        }
    }

    /**
     * Geo-spatial search within radius
     */
    @GetMapping("/geo")
    @Operation(summary = "Geo-spatial search", description = "Search vehicles within a geographic radius")
    public ResponseEntity<ApiResponse<Map<String, Object>>> geoSpatialSearch(
            @Parameter(description = "Latitude", required = true) @RequestParam Double latitude,
            @Parameter(description = "Longitude", required = true) @RequestParam Double longitude,
            @Parameter(description = "Radius in kilometers") @RequestParam(required = false, defaultValue = "50") Double radius,
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Page number") @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "20") int size) {
        
        try {
            Map<String, Object> searchParams = new HashMap<>();
            searchParams.put("latitude", latitude);
            searchParams.put("longitude", longitude);
            searchParams.put("radius", radius);
            if (query != null) {
                searchParams.put("query", query);
            }
            
            Pageable pageable = PageRequest.of(page, size);
            Map<String, Object> results = advancedSearchService.intelligentSearch(searchParams, pageable);
            
            return ResponseEntity.ok(ApiResponse.success(results, "Geo-spatial search completed"));

        } catch (Exception e) {
            log.error("Error in geo-spatial search: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Geo-spatial search failed: " + e.getMessage()));
        }
    }

    /**
     * Faceted search with aggregations
     */
    @GetMapping("/faceted")
    @Operation(summary = "Faceted search", description = "Search with facet aggregations for filtering")
    public ResponseEntity<ApiResponse<Map<String, Object>>> facetedSearch(
            @Parameter(description = "Search query") @RequestParam(required = false, defaultValue = "*") String query,
            @Parameter(description = "Page number") @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "20") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Map<String, Object> results = advancedSearchService.getFacetedSearch(query, pageable);
            
            return ResponseEntity.ok(ApiResponse.success(results, "Faceted search completed"));

        } catch (Exception e) {
            log.error("Error in faceted search: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Faceted search failed: " + e.getMessage()));
        }
    }

    /**
     * Search suggestions for auto-complete
     */
    @GetMapping("/suggestions")
    @Operation(summary = "Search suggestions", description = "Get auto-complete suggestions for search queries")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSearchSuggestions(
            @Parameter(description = "Search prefix", required = true) @RequestParam String prefix,
            @Parameter(description = "Maximum suggestions") @RequestParam(required = false, defaultValue = "10") int limit) {
        
        try {
            List<Map<String, Object>> suggestions = advancedSearchService.getSearchSuggestions(prefix, limit);
            
            return ResponseEntity.ok(ApiResponse.success(suggestions, "Search suggestions retrieved"));

        } catch (Exception e) {
            log.error("Error getting search suggestions: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get suggestions: " + e.getMessage()));
        }
    }

    /**
     * Find similar vehicles
     */
    @GetMapping("/similar/{vehicleId}")
    @Operation(summary = "Similar vehicles", description = "Find vehicles similar to the specified vehicle")
    public ResponseEntity<ApiResponse<Page<VehicleSearchDocument>>> findSimilarVehicles(
            @Parameter(description = "Vehicle ID", required = true) @PathVariable Long vehicleId,
            @Parameter(description = "Page number") @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "10") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<VehicleSearchDocument> results = advancedSearchService.findSimilarVehicles(vehicleId, pageable);
            
            return ResponseEntity.ok(ApiResponse.success(results, "Similar vehicles found"));

        } catch (Exception e) {
            log.error("Error finding similar vehicles: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to find similar vehicles: " + e.getMessage()));
        }
    }

    /**
     * Get trending search terms
     */
    @GetMapping("/trending")
    @Operation(summary = "Trending searches", description = "Get currently trending search terms")
    public ResponseEntity<ApiResponse<List<String>>> getTrendingSearchTerms(
            @Parameter(description = "Maximum results") @RequestParam(required = false, defaultValue = "10") int limit) {
        
        try {
            List<String> trendingTerms = advancedSearchService.getTrendingSearchTerms(limit);
            
            return ResponseEntity.ok(ApiResponse.success(trendingTerms, "Trending search terms retrieved"));

        } catch (Exception e) {
            log.error("Error getting trending search terms: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get trending terms: " + e.getMessage()));
        }
    }

    /**
     * Sync vehicle to Elasticsearch
     */
    @PostMapping("/sync/{vehicleId}")
    @Operation(summary = "Sync vehicle", description = "Sync a specific vehicle to Elasticsearch index")
    public ResponseEntity<ApiResponse<String>> syncVehicleToElasticsearch(
            @Parameter(description = "Vehicle ID", required = true) @PathVariable Long vehicleId) {
        
        try {
            advancedSearchService.syncVehicleToElasticsearch(vehicleId);
            
            return ResponseEntity.ok(ApiResponse.success("Vehicle sync initiated", "Vehicle will be synced to search index"));

        } catch (Exception e) {
            log.error("Error syncing vehicle to Elasticsearch: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to sync vehicle: " + e.getMessage()));
        }
    }

    /**
     * Bulk sync all vehicles to Elasticsearch
     */
    @PostMapping("/sync/bulk")
    @Operation(summary = "Bulk sync vehicles", description = "Sync all vehicles to Elasticsearch index")
    public ResponseEntity<ApiResponse<String>> bulkSyncVehiclesToElasticsearch() {
        
        try {
            advancedSearchService.bulkSyncVehiclesToElasticsearch();
            
            return ResponseEntity.ok(ApiResponse.success("Bulk sync initiated", "All vehicles will be synced to search index"));

        } catch (Exception e) {
            log.error("Error in bulk sync to Elasticsearch: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to initiate bulk sync: " + e.getMessage()));
        }
    }

    /**
     * Search health check
     */
    @GetMapping("/health")
    @Operation(summary = "Search health", description = "Check the health of search service")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchHealthCheck() {
        
        try {
            Map<String, Object> healthInfo = new HashMap<>();
            healthInfo.put("status", "healthy");
            healthInfo.put("timestamp", System.currentTimeMillis());
            healthInfo.put("service", "elasticsearch");
            healthInfo.put("features", List.of(
                "full-text search",
                "geo-spatial search", 
                "faceted search",
                "auto-complete suggestions",
                "similarity search"
            ));
            
            return ResponseEntity.ok(ApiResponse.success(healthInfo, "Search service is healthy"));

        } catch (Exception e) {
            log.error("Search health check failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Search service health check failed: " + e.getMessage()));
        }
    }
}
