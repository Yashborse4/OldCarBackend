package com.carselling.oldcar.service;

import com.carselling.oldcar.document.VehicleSearchDocument;
import com.carselling.oldcar.entity.Vehicle;
import com.carselling.oldcar.repository.VehicleRepository;
import com.carselling.oldcar.repository.search.VehicleSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for advanced search operations using Elasticsearch
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvancedSearchService {

    private final VehicleSearchRepository vehicleSearchRepository;
    private final VehicleRepository vehicleRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    // private final PerformanceMonitoringService performanceService; // Disabled

    /**
     * Perform intelligent search with multiple strategies
     */
    public Map<String, Object> intelligentSearch(Map<String, Object> searchParams, Pageable pageable) {
        long startTime = System.currentTimeMillis();
        
        try {
            String query = (String) searchParams.get("query");
            BigDecimal minPrice = parseDecimal(searchParams.get("minPrice"));
            BigDecimal maxPrice = parseDecimal(searchParams.get("maxPrice"));
            Integer minYear = parseInteger(searchParams.get("minYear"));
            Integer maxYear = parseInteger(searchParams.get("maxYear"));
            String make = (String) searchParams.get("make");
            String model = (String) searchParams.get("model");
            String fuelType = (String) searchParams.get("fuelType");
            String transmission = (String) searchParams.get("transmission");
            String bodyType = (String) searchParams.get("bodyType");
            String location = (String) searchParams.get("location");
            Boolean verified = parseBoolean(searchParams.get("verified"));
            Boolean featured = parseBoolean(searchParams.get("featured"));
            Double latitude = parseDouble(searchParams.get("latitude"));
            Double longitude = parseDouble(searchParams.get("longitude"));
            Double radius = parseDouble(searchParams.get("radius"));

            Page<VehicleSearchDocument> results;

            // Choose search strategy based on parameters
            if (latitude != null && longitude != null && radius != null) {
                results = performGeoSpatialSearch(query, latitude, longitude, radius, searchParams, pageable);
            } else if (query != null && !query.trim().isEmpty()) {
                results = performFullTextSearch(query, searchParams, pageable);
            } else {
                results = performFilterSearch(searchParams, pageable);
            }

            // Build response with facets and suggestions
            Map<String, Object> response = buildSearchResponse(results, query);
            
            // Record performance metrics
            long executionTime = System.currentTimeMillis() - startTime;
            performanceService.recordApiCall("/search/intelligent", executionTime, true);
            
            return response;

        } catch (Exception e) {
            log.error("Error performing intelligent search: {}", e.getMessage(), e);
            performanceService.recordApiCall("/search/intelligent", 
                System.currentTimeMillis() - startTime, false);
            throw new RuntimeException("Search service temporarily unavailable", e);
        }
    }

    /**
     * Get search suggestions for auto-complete
     */
    public List<Map<String, Object>> getSearchSuggestions(String prefix, int limit) {
        try {
            List<VehicleSearchDocument> suggestions = vehicleSearchRepository.findSuggestions(prefix);
            
            return suggestions.stream()
                    .limit(limit)
                    .map(this::buildSuggestionResponse)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error getting search suggestions: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Get faceted search results with aggregations
     */
    public Map<String, Object> getFacetedSearch(String query, Pageable pageable) {
        try {
            NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                    .withQuery(buildMultiMatchQuery(query))
                    .withPageable(pageable)
                    .build();

            SearchHits<VehicleSearchDocument> searchHits = elasticsearchOperations.search(searchQuery, VehicleSearchDocument.class);
            
            // Build facets
            Map<String, Object> facets = buildFacets(query);
            
            Map<String, Object> response = new HashMap<>();
            response.put("results", searchHits.getSearchHits().stream()
                    .map(hit -> hit.getContent())
                    .collect(Collectors.toList()));
            response.put("facets", facets);
            response.put("total", searchHits.getTotalHits());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error performing faceted search: {}", e.getMessage(), e);
            throw new RuntimeException("Faceted search service temporarily unavailable", e);
        }
    }

    /**
     * Find similar vehicles based on a vehicle ID
     */
    public Page<VehicleSearchDocument> findSimilarVehicles(Long vehicleId, Pageable pageable) {
        try {
            Optional<VehicleSearchDocument> vehicleOpt = vehicleSearchRepository.findById(vehicleId.toString());
            
            if (vehicleOpt.isEmpty()) {
                return Page.empty(pageable);
            }
            
            VehicleSearchDocument vehicle = vehicleOpt.get();
            BigDecimal priceMin = vehicle.getPrice().multiply(BigDecimal.valueOf(0.8)); // -20%
            BigDecimal priceMax = vehicle.getPrice().multiply(BigDecimal.valueOf(1.2)); // +20%
            
            return vehicleSearchRepository.findSimilarVehicles(
                vehicle.getMake(), 
                vehicle.getModel(), 
                priceMin, 
                priceMax, 
                pageable
            );
            
        } catch (Exception e) {
            log.error("Error finding similar vehicles: {}", e.getMessage(), e);
            return Page.empty(pageable);
        }
    }

    /**
     * Get trending search terms
     */
    public List<String> getTrendingSearchTerms(int limit) {
        // This would typically come from search analytics
        // For now, return popular vehicle makes/models
        return List.of(
            "Toyota Camry", "Honda Civic", "BMW 3 Series", "Mercedes C-Class",
            "Audi A4", "Ford F-150", "Chevrolet Silverado", "Tesla Model 3",
            "Hyundai Elantra", "Nissan Altima"
        ).stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * Sync vehicle data from MySQL to Elasticsearch
     */
    @Async
    public void syncVehicleToElasticsearch(Long vehicleId) {
        try {
            Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId);
            
            if (vehicleOpt.isPresent()) {
                Vehicle vehicle = vehicleOpt.get();
                VehicleSearchDocument searchDoc = mapVehicleToSearchDocument(vehicle);
                vehicleSearchRepository.save(searchDoc);
                
                log.info("Vehicle {} synced to Elasticsearch", vehicleId);
            }
            
        } catch (Exception e) {
            log.error("Error syncing vehicle {} to Elasticsearch: {}", vehicleId, e.getMessage(), e);
        }
    }

    /**
     * Remove vehicle from Elasticsearch index
     */
    public void removeVehicleFromElasticsearch(Long vehicleId) {
        try {
            vehicleSearchRepository.deleteById(vehicleId.toString());
            log.info("Vehicle {} removed from Elasticsearch", vehicleId);
        } catch (Exception e) {
            log.error("Error removing vehicle {} from Elasticsearch: {}", vehicleId, e.getMessage(), e);
            throw new RuntimeException("Failed to remove vehicle from search index", e);
        }
    }

    /**
     * Bulk sync all vehicles to Elasticsearch
     */
    @Async
    public void bulkSyncVehiclesToElasticsearch() {
        try {
            log.info("Starting bulk sync of vehicles to Elasticsearch");
            
            List<Vehicle> allVehicles = vehicleRepository.findByIsActiveTrue();
            List<VehicleSearchDocument> searchDocuments = allVehicles.stream()
                    .map(this::mapVehicleToSearchDocument)
                    .collect(Collectors.toList());
            
            vehicleSearchRepository.saveAll(searchDocuments);
            
            log.info("Bulk sync completed. Synced {} vehicles to Elasticsearch", searchDocuments.size());
            
        } catch (Exception e) {
            log.error("Error during bulk sync to Elasticsearch: {}", e.getMessage(), e);
        }
    }

    // Private helper methods

    private Page<VehicleSearchDocument> performFullTextSearch(String query, Map<String, Object> params, Pageable pageable) {
        // Use boosting search for better relevance
        return vehicleSearchRepository.findWithBoostingSearch(query, pageable);
    }

    private Page<VehicleSearchDocument> performGeoSpatialSearch(String query, Double lat, Double lon, 
                                                              Double radius, Map<String, Object> params, Pageable pageable) {
        if (query != null && !query.trim().isEmpty()) {
            // Combine geo and text search - this would require a more complex query
            return vehicleSearchRepository.findByLocationWithinRadius(lat, lon, radius, pageable);
        } else {
            return vehicleSearchRepository.findByLocationWithinRadius(lat, lon, radius, pageable);
        }
    }

    private Page<VehicleSearchDocument> performFilterSearch(Map<String, Object> params, Pageable pageable) {
        String make = (String) params.get("make");
        String model = (String) params.get("model");
        
        if (make != null && model != null) {
            return vehicleSearchRepository.findByMakeAndModel(make, model, pageable);
        } else if (make != null) {
            return vehicleSearchRepository.findByMake(make, pageable);
        } else {
            return vehicleSearchRepository.findByStatusOrderByCreatedAtDesc("ACTIVE", pageable);
        }
    }

    private Map<String, Object> buildSearchResponse(Page<VehicleSearchDocument> results, String query) {
        Map<String, Object> response = new HashMap<>();
        
        response.put("results", results.getContent());
        response.put("totalElements", results.getTotalElements());
        response.put("totalPages", results.getTotalPages());
        response.put("currentPage", results.getNumber());
        response.put("pageSize", results.getSize());
        
        // Add search metadata
        response.put("searchQuery", query);
        response.put("searchTime", System.currentTimeMillis());
        response.put("hasNext", results.hasNext());
        response.put("hasPrevious", results.hasPrevious());
        
        return response;
    }

    private Map<String, Object> buildSuggestionResponse(VehicleSearchDocument vehicle) {
        Map<String, Object> suggestion = new HashMap<>();
        suggestion.put("text", vehicle.getMake() + " " + vehicle.getModel());
        suggestion.put("make", vehicle.getMake());
        suggestion.put("model", vehicle.getModel());
        suggestion.put("year", vehicle.getYear());
        suggestion.put("price", vehicle.getPrice());
        return suggestion;
    }

    private org.elasticsearch.index.query.QueryBuilder buildMultiMatchQuery(String query) {
        return org.elasticsearch.index.query.QueryBuilders.multiMatchQuery(query)
                .field("make", 3.0f)
                .field("model", 3.0f)
                .field("searchableText", 2.0f)
                .field("description", 1.0f)
                .field("features", 1.0f)
                .field("tags", 1.0f)
                .type(org.elasticsearch.index.query.MultiMatchQueryBuilder.Type.BEST_FIELDS)
                .fuzziness(org.elasticsearch.common.unit.Fuzziness.AUTO);
    }

    private Map<String, Object> buildFacets(String query) {
        // This would typically use Elasticsearch aggregations
        Map<String, Object> facets = new HashMap<>();
        
        facets.put("makes", getMakesFacet());
        facets.put("fuelTypes", getFuelTypesFacet());
        facets.put("transmissions", getTransmissionsFacet());
        facets.put("bodyTypes", getBodyTypesFacet());
        facets.put("priceRanges", getPriceRangesFacet());
        facets.put("yearRanges", getYearRangesFacet());
        
        return facets;
    }

    private List<Map<String, Object>> getMakesFacet() {
        return List.of(
            Map.of("key", "Toyota", "count", 150),
            Map.of("key", "Honda", "count", 120),
            Map.of("key", "BMW", "count", 80),
            Map.of("key", "Mercedes", "count", 60),
            Map.of("key", "Audi", "count", 45)
        );
    }

    private List<Map<String, Object>> getFuelTypesFacet() {
        return List.of(
            Map.of("key", "PETROL", "count", 200),
            Map.of("key", "DIESEL", "count", 150),
            Map.of("key", "ELECTRIC", "count", 80),
            Map.of("key", "HYBRID", "count", 60)
        );
    }

    private List<Map<String, Object>> getTransmissionsFacet() {
        return List.of(
            Map.of("key", "AUTOMATIC", "count", 300),
            Map.of("key", "MANUAL", "count", 190)
        );
    }

    private List<Map<String, Object>> getBodyTypesFacet() {
        return List.of(
            Map.of("key", "SEDAN", "count", 180),
            Map.of("key", "SUV", "count", 160),
            Map.of("key", "HATCHBACK", "count", 100),
            Map.of("key", "COUPE", "count", 50)
        );
    }

    private List<Map<String, Object>> getPriceRangesFacet() {
        return List.of(
            Map.of("key", "0-10000", "count", 120),
            Map.of("key", "10000-25000", "count", 200),
            Map.of("key", "25000-50000", "count", 150),
            Map.of("key", "50000+", "count", 80)
        );
    }

    private List<Map<String, Object>> getYearRangesFacet() {
        return List.of(
            Map.of("key", "2020-2024", "count", 180),
            Map.of("key", "2015-2019", "count", 220),
            Map.of("key", "2010-2014", "count", 150),
            Map.of("key", "2000-2009", "count", 100)
        );
    }

    private VehicleSearchDocument mapVehicleToSearchDocument(Vehicle vehicle) {
        VehicleSearchDocument doc = VehicleSearchDocument.builder()
                .id(vehicle.getId().toString())
                .vehicleId(vehicle.getId())
                .make(vehicle.getMake())
                .model(vehicle.getModel())
                .year(vehicle.getYear())
                .price(vehicle.getPrice())
                .mileage(vehicle.getMileage())
                .fuelType(vehicle.getFuelType())
                .transmission(vehicle.getTransmission())
                .description(vehicle.getDescription())
                .location(vehicle.getLocation())
                .ownerId(vehicle.getOwner().getId())
                .ownerType("INDIVIDUAL") // Default
                .status("ACTIVE")
                .isVerified(false) // Default
                .isFeatured(false) // Default
                .createdAt(vehicle.getCreatedAt())
                .updatedAt(vehicle.getUpdatedAt())
                .build();

        // Build searchable text and suggestions
        doc.buildSearchableText();
        doc.calculateSearchBoost();
        doc.setSuggest(VehicleSearchDocument.createSuggestion(
            vehicle.getMake(), vehicle.getModel(), vehicle.getYear()));

        return doc;
    }

    // Utility methods for parsing request parameters
    private BigDecimal parseDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(Object value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean parseBoolean(Object value) {
        if (value == null) return null;
        return Boolean.parseBoolean(value.toString());
    }
}
