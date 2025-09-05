package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.vehicle.*;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.VehicleServiceEnhanced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * Enhanced Vehicle Controller with advanced search, recommendations, and analytics
 */
@RestController
@RequestMapping("/api/v2/vehicles")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class VehicleControllerEnhanced {

    private final VehicleServiceEnhanced vehicleServiceEnhanced;

    // ========================= ADVANCED SEARCH =========================

    /**
     * Advanced vehicle search with multiple filters
     */
    @PostMapping("/search/advanced")
    public ResponseEntity<?> advancedSearch(
            @Valid @RequestBody VehicleSearchCriteria criteria,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        try {
            Page<VehicleSearchResultDto> results = vehicleServiceEnhanced.advancedSearch(criteria, pageable);
            
            return ResponseEntity.ok(Map.of(
                "results", results.getContent(),
                "pagination", Map.of(
                    "totalElements", results.getTotalElements(),
                    "totalPages", results.getTotalPages(),
                    "currentPage", results.getNumber(),
                    "size", results.getSize(),
                    "hasNext", results.hasNext(),
                    "hasPrevious", results.hasPrevious()
                ),
                "searchCriteria", criteria
            ));
        } catch (Exception e) {
            log.error("Error in advanced search: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Search failed", "message", e.getMessage()));
        }
    }

    /**
     * Get search suggestions based on partial query
     */
    @GetMapping("/search/suggestions")
    public ResponseEntity<?> getSearchSuggestions(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            // Implement search suggestions logic here
            List<String> suggestions = List.of(
                query + " Honda", query + " Toyota", query + " BMW",
                query + " Mercedes", query + " Audi"
            );
            
            return ResponseEntity.ok(Map.of(
                "suggestions", suggestions.subList(0, Math.min(suggestions.size(), limit)),
                "query", query
            ));
        } catch (Exception e) {
            log.error("Error getting search suggestions: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to get suggestions", "message", e.getMessage()));
        }
    }

    // ========================= RECOMMENDATIONS =========================

    /**
     * Get personalized vehicle recommendations for user
     */
    @GetMapping("/recommendations")
    public ResponseEntity<?> getPersonalizedRecommendations(
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            List<VehicleRecommendationDto> recommendations = 
                vehicleServiceEnhanced.getRecommendationsForUser(currentUser.getId(), limit);
            
            return ResponseEntity.ok(Map.of(
                "recommendations", recommendations,
                "userId", currentUser.getId(),
                "count", recommendations.size()
            ));
        } catch (Exception e) {
            log.error("Error getting recommendations: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get recommendations", "message", e.getMessage()));
        }
    }

    /**
     * Get similar vehicles based on a specific vehicle
     */
    @GetMapping("/{vehicleId}/similar")
    public ResponseEntity<?> getSimilarVehicles(
            @PathVariable Long vehicleId,
            @RequestParam(defaultValue = "8") int limit) {
        try {
            List<VehicleSummaryDto> similarVehicles = 
                vehicleServiceEnhanced.getSimilarVehicles(vehicleId, limit);
            
            return ResponseEntity.ok(Map.of(
                "similarVehicles", similarVehicles,
                "baseVehicleId", vehicleId,
                "count", similarVehicles.size()
            ));
        } catch (Exception e) {
            log.error("Error getting similar vehicles: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to get similar vehicles", "message", e.getMessage()));
        }
    }

    // ========================= FAVORITES MANAGEMENT =========================

    /**
     * Add vehicle to user's favorites
     */
    @PostMapping("/{vehicleId}/favorites")
    public ResponseEntity<?> addToFavorites(
            @PathVariable Long vehicleId,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            vehicleServiceEnhanced.addToFavorites(currentUser.getId(), vehicleId);
            
            return ResponseEntity.ok(Map.of(
                "message", "Vehicle added to favorites successfully",
                "vehicleId", vehicleId,
                "userId", currentUser.getId()
            ));
        } catch (Exception e) {
            log.error("Error adding to favorites: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to add to favorites", "message", e.getMessage()));
        }
    }

    /**
     * Remove vehicle from user's favorites
     */
    @DeleteMapping("/{vehicleId}/favorites")
    public ResponseEntity<?> removeFromFavorites(
            @PathVariable Long vehicleId,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            vehicleServiceEnhanced.removeFromFavorites(currentUser.getId(), vehicleId);
            
            return ResponseEntity.ok(Map.of(
                "message", "Vehicle removed from favorites successfully",
                "vehicleId", vehicleId,
                "userId", currentUser.getId()
            ));
        } catch (Exception e) {
            log.error("Error removing from favorites: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to remove from favorites", "message", e.getMessage()));
        }
    }

    /**
     * Get user's favorite vehicles
     */
    @GetMapping("/favorites")
    public ResponseEntity<?> getUserFavorites(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            Page<VehicleSummaryDto> favorites = 
                vehicleServiceEnhanced.getUserFavorites(currentUser.getId(), pageable);
            
            return ResponseEntity.ok(Map.of(
                "favorites", favorites.getContent(),
                "pagination", Map.of(
                    "totalElements", favorites.getTotalElements(),
                    "totalPages", favorites.getTotalPages(),
                    "currentPage", favorites.getNumber(),
                    "size", favorites.getSize()
                ),
                "userId", currentUser.getId()
            ));
        } catch (Exception e) {
            log.error("Error getting user favorites: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get favorites", "message", e.getMessage()));
        }
    }

    // ========================= TRENDING AND ANALYTICS =========================

    /**
     * Get trending vehicles
     */
    @GetMapping("/trending")
    public ResponseEntity<?> getTrendingVehicles(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "24") int hours) {
        try {
            List<VehicleTrendingDto> trendingVehicles = 
                vehicleServiceEnhanced.getTrendingVehicles(limit);
            
            return ResponseEntity.ok(Map.of(
                "trending", trendingVehicles,
                "timeframe", hours + " hours",
                "count", trendingVehicles.size(),
                "generatedAt", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Error getting trending vehicles: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get trending vehicles", "message", e.getMessage()));
        }
    }

    /**
     * Get vehicles by location with radius search
     */
    @GetMapping("/nearby")
    public ResponseEntity<?> getVehiclesByLocation(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "50.0") Double radiusKm,
            @PageableDefault(size = 20) Pageable pageable) {
        try {
            Page<VehicleSearchResultDto> nearbyVehicles = 
                vehicleServiceEnhanced.getVehiclesByLocation(latitude, longitude, radiusKm, pageable);
            
            return ResponseEntity.ok(Map.of(
                "vehicles", nearbyVehicles.getContent(),
                "location", Map.of(
                    "latitude", latitude,
                    "longitude", longitude,
                    "radiusKm", radiusKm
                ),
                "pagination", Map.of(
                    "totalElements", nearbyVehicles.getTotalElements(),
                    "totalPages", nearbyVehicles.getTotalPages(),
                    "currentPage", nearbyVehicles.getNumber()
                )
            ));
        } catch (Exception e) {
            log.error("Error getting nearby vehicles: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to get nearby vehicles", "message", e.getMessage()));
        }
    }

    /**
     * Track vehicle view for analytics
     */
    @PostMapping("/{vehicleId}/view")
    public ResponseEntity<?> trackVehicleView(
            @PathVariable Long vehicleId,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            vehicleServiceEnhanced.trackVehicleView(vehicleId, currentUser.getId());
            
            return ResponseEntity.ok(Map.of(
                "message", "Vehicle view tracked successfully",
                "vehicleId", vehicleId,
                "userId", currentUser.getId(),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Error tracking vehicle view: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to track view", "message", e.getMessage()));
        }
    }

    // ========================= PRICE ANALYSIS =========================

    /**
     * Get vehicle price analysis and market insights
     */
    @PostMapping("/price-analysis")
    public ResponseEntity<?> getPriceAnalysis(
            @Valid @RequestBody VehiclePriceAnalysisRequest request) {
        try {
            VehiclePriceAnalysisDto analysis = vehicleServiceEnhanced.getPriceAnalysis(request);
            
            return ResponseEntity.ok(Map.of(
                "analysis", analysis,
                "request", request,
                "generatedAt", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Error getting price analysis: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to get price analysis", "message", e.getMessage()));
        }
    }

    // ========================= VEHICLE STATISTICS =========================

    /**
     * Get vehicle statistics dashboard
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getVehicleStatistics(
            @RequestParam(required = false) String make,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Integer year) {
        try {
            // Implement statistics logic
            Map<String, Object> statistics = Map.of(
                "totalVehicles", 1500,
                "activeListings", 1200,
                "averagePrice", 25000,
                "popularMakes", List.of("Honda", "Toyota", "BMW"),
                "priceRanges", Map.of(
                    "under20k", 400,
                    "20k-40k", 600,
                    "40k-60k", 300,
                    "above60k", 200
                ),
                "generatedAt", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Error getting vehicle statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get statistics", "message", e.getMessage()));
        }
    }

    // ========================= HEALTH AND UTILITIES =========================

    /**
     * Health check for vehicle service
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        try {
            return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Vehicle Service Enhanced",
                "timestamp", System.currentTimeMillis(),
                "version", "2.0"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "DOWN", "error", e.getMessage()));
        }
    }
}
