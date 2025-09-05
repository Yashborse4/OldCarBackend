package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.ApiResponse;
import com.carselling.oldcar.entity.Vehicle;
import com.carselling.oldcar.repository.VehicleRepository;
import com.carselling.oldcar.service.*;
import com.carselling.oldcar.service.MachineLearningRecommendationService.VehicleRecommendation;
import com.carselling.oldcar.service.PricePredictionService.PriceAnalysis;
import com.carselling.oldcar.service.PricePredictionService.MarketTrendAnalysis;
import com.carselling.oldcar.service.UserBehaviorTrackingService.UserAnalytics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Machine Learning & AI Controller
 * Exposes AI-powered features including recommendations, price prediction, and analytics
 */
@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Machine Learning & AI", description = "AI-powered features for intelligent vehicle recommendations, price predictions, and user analytics")
public class MachineLearningController {

    private final MachineLearningRecommendationService mlRecommendationService;
    private final PricePredictionService pricePredictionService;
    private final UserBehaviorTrackingService behaviorTrackingService;
    private final VehicleRepository vehicleRepository;

    /**
     * Get personalized vehicle recommendations for user
     */
    @GetMapping("/recommendations/personalized")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get personalized recommendations", 
               description = "AI-powered personalized vehicle recommendations based on user behavior and preferences")
    public ResponseEntity<ApiResponse<Page<VehicleRecommendation>>> getPersonalizedRecommendations(
            @Parameter(description = "User ID") @RequestParam Long userId,
            @Parameter(description = "Page number") @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "10") int size) {
        
        try {
            log.info("Getting personalized recommendations for user: {}", userId);
            
            Pageable pageable = PageRequest.of(page, size);
            Page<VehicleRecommendation> recommendations = 
                    mlRecommendationService.getPersonalizedRecommendations(userId, pageable);
            
            return ResponseEntity.ok(ApiResponse.success(recommendations, 
                    "Personalized recommendations retrieved successfully"));

        } catch (Exception e) {
            log.error("Error getting personalized recommendations for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get recommendations: " + e.getMessage()));
        }
    }

    /**
     * Get similar vehicles based on a specific vehicle
     */
    @GetMapping("/recommendations/similar/{vehicleId}")
    @Operation(summary = "Get similar vehicles", 
               description = "Find vehicles similar to the specified vehicle using ML algorithms")
    public ResponseEntity<ApiResponse<Page<VehicleRecommendation>>> getSimilarVehicles(
            @Parameter(description = "Target vehicle ID") @PathVariable Long vehicleId,
            @Parameter(description = "Page number") @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "10") int size) {
        
        try {
            log.info("Getting similar vehicles for vehicle: {}", vehicleId);
            
            Pageable pageable = PageRequest.of(page, size);
            Page<VehicleRecommendation> similarVehicles = 
                    mlRecommendationService.getSimilarVehicles(vehicleId, pageable);
            
            return ResponseEntity.ok(ApiResponse.success(similarVehicles, 
                    "Similar vehicles retrieved successfully"));

        } catch (Exception e) {
            log.error("Error getting similar vehicles for {}: {}", vehicleId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get similar vehicles: " + e.getMessage()));
        }
    }

    /**
     * Get trending vehicles with ML-based popularity scoring
     */
    @GetMapping("/recommendations/trending")
    @Operation(summary = "Get trending vehicles", 
               description = "Get currently trending vehicles based on ML popularity algorithms")
    public ResponseEntity<ApiResponse<Page<VehicleRecommendation>>> getTrendingVehicles(
            @Parameter(description = "Page number") @RequestParam(required = false, defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(required = false, defaultValue = "10") int size) {
        
        try {
            log.info("Getting trending vehicles");
            
            Pageable pageable = PageRequest.of(page, size);
            Page<VehicleRecommendation> trendingVehicles = 
                    mlRecommendationService.getTrendingVehicles(pageable);
            
            return ResponseEntity.ok(ApiResponse.success(trendingVehicles, 
                    "Trending vehicles retrieved successfully"));

        } catch (Exception e) {
            log.error("Error getting trending vehicles: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get trending vehicles: " + e.getMessage()));
        }
    }

    /**
     * Get market-based recommendations (deals, price drops, etc.)
     */
    @GetMapping("/recommendations/market")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get market recommendations", 
               description = "Get market-based recommendations including deals, price drops, and high-demand vehicles")
    public ResponseEntity<ApiResponse<List<VehicleRecommendation>>> getMarketRecommendations(
            @Parameter(description = "User ID") @RequestParam Long userId) {
        
        try {
            log.info("Getting market recommendations for user: {}", userId);
            
            List<VehicleRecommendation> marketRecommendations = 
                    mlRecommendationService.getMarketBasedRecommendations(userId);
            
            return ResponseEntity.ok(ApiResponse.success(marketRecommendations, 
                    "Market recommendations retrieved successfully"));

        } catch (Exception e) {
            log.error("Error getting market recommendations for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get market recommendations: " + e.getMessage()));
        }
    }

    /**
     * Predict vehicle price using AI algorithms
     */
    @GetMapping("/price/predict/{vehicleId}")
    @Operation(summary = "Predict vehicle price", 
               description = "AI-powered price prediction for a specific vehicle")
    public ResponseEntity<ApiResponse<Double>> predictVehiclePrice(
            @Parameter(description = "Vehicle ID") @PathVariable Long vehicleId) {
        
        try {
            log.info("Predicting price for vehicle: {}", vehicleId);
            
            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                    .orElseThrow(() -> new RuntimeException("Vehicle not found"));
            
            double predictedPrice = pricePredictionService.predictPrice(vehicle);
            
            return ResponseEntity.ok(ApiResponse.success(predictedPrice, 
                    "Price prediction completed successfully"));

        } catch (Exception e) {
            log.error("Error predicting price for vehicle {}: {}", vehicleId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to predict price: " + e.getMessage()));
        }
    }

    /**
     * Get comprehensive price analysis for a vehicle
     */
    @GetMapping("/price/analyze/{vehicleId}")
    @Operation(summary = "Analyze vehicle price", 
               description = "Comprehensive AI-powered price analysis including market positioning and recommendations")
    public ResponseEntity<ApiResponse<PriceAnalysis>> analyzeVehiclePrice(
            @Parameter(description = "Vehicle ID") @PathVariable Long vehicleId) {
        
        try {
            log.info("Analyzing price for vehicle: {}", vehicleId);
            
            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                    .orElseThrow(() -> new RuntimeException("Vehicle not found"));
            
            PriceAnalysis analysis = pricePredictionService.analyzePriceComprehensively(vehicle);
            
            return ResponseEntity.ok(ApiResponse.success(analysis, 
                    "Price analysis completed successfully"));

        } catch (Exception e) {
            log.error("Error analyzing price for vehicle {}: {}", vehicleId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to analyze price: " + e.getMessage()));
        }
    }

    /**
     * Get market trends for similar vehicles
     */
    @GetMapping("/price/trends")
    @Operation(summary = "Get market trends", 
               description = "Analyze market price trends for vehicles by make, model, and year")
    public ResponseEntity<ApiResponse<MarketTrendAnalysis>> getMarketTrends(
            @Parameter(description = "Vehicle make") @RequestParam String make,
            @Parameter(description = "Vehicle model") @RequestParam String model,
            @Parameter(description = "Vehicle year") @RequestParam int year,
            @Parameter(description = "Analysis period in months") @RequestParam(required = false, defaultValue = "6") int months) {
        
        try {
            log.info("Analyzing market trends for {} {} {}", make, model, year);
            
            MarketTrendAnalysis trends = pricePredictionService.getMarketTrends(make, model, year, months);
            
            return ResponseEntity.ok(ApiResponse.success(trends, 
                    "Market trends analysis completed successfully"));

        } catch (Exception e) {
            log.error("Error analyzing market trends: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to analyze market trends: " + e.getMessage()));
        }
    }

    /**
     * Batch predict prices for multiple vehicles
     */
    @PostMapping("/price/batch-predict")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DEALER')")
    @Operation(summary = "Batch predict prices", 
               description = "Predict prices for multiple vehicles asynchronously")
    public ResponseEntity<ApiResponse<String>> batchPredictPrices(
            @Parameter(description = "List of vehicle IDs") @RequestBody List<Long> vehicleIds) {
        
        try {
            log.info("Starting batch price prediction for {} vehicles", vehicleIds.size());
            
            List<Vehicle> vehicles = vehicleRepository.findAllById(vehicleIds);
            
            CompletableFuture<Map<Long, Double>> future = 
                    pricePredictionService.batchPredictPrices(vehicles);
            
            return ResponseEntity.ok(ApiResponse.success("Batch prediction initiated", 
                    "Price predictions are being processed in the background"));

        } catch (Exception e) {
            log.error("Error in batch price prediction: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to initiate batch prediction: " + e.getMessage()));
        }
    }

    /**
     * Track user behavior - vehicle view
     */
    @PostMapping("/behavior/track/view")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Track vehicle view", 
               description = "Track user viewing a vehicle for ML learning")
    public ResponseEntity<ApiResponse<String>> trackVehicleView(
            @Parameter(description = "User ID") @RequestParam Long userId,
            @Parameter(description = "Vehicle ID") @RequestParam Long vehicleId) {
        
        try {
            log.debug("Tracking vehicle view - User: {}, Vehicle: {}", userId, vehicleId);
            
            behaviorTrackingService.trackVehicleView(userId, vehicleId);
            
            return ResponseEntity.ok(ApiResponse.success("View tracked", 
                    "Vehicle view has been recorded for ML learning"));

        } catch (Exception e) {
            log.error("Error tracking vehicle view: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to track view: " + e.getMessage()));
        }
    }

    /**
     * Track user behavior - search query
     */
    @PostMapping("/behavior/track/search")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Track search query", 
               description = "Track user search behavior for personalization")
    public ResponseEntity<ApiResponse<String>> trackSearchQuery(
            @Parameter(description = "User ID") @RequestParam Long userId,
            @Parameter(description = "Search query") @RequestParam String query,
            @Parameter(description = "Search filters") @RequestBody(required = false) Map<String, Object> filters) {
        
        try {
            log.debug("Tracking search query - User: {}, Query: {}", userId, query);
            
            behaviorTrackingService.trackSearchQuery(userId, query, filters);
            
            return ResponseEntity.ok(ApiResponse.success("Search tracked", 
                    "Search query has been recorded for personalization"));

        } catch (Exception e) {
            log.error("Error tracking search query: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to track search: " + e.getMessage()));
        }
    }

    /**
     * Track user behavior - vehicle click
     */
    @PostMapping("/behavior/track/click")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Track vehicle click", 
               description = "Track user clicking on a vehicle from various contexts")
    public ResponseEntity<ApiResponse<String>> trackVehicleClick(
            @Parameter(description = "User ID") @RequestParam Long userId,
            @Parameter(description = "Vehicle ID") @RequestParam Long vehicleId,
            @Parameter(description = "Click context") @RequestParam String context) {
        
        try {
            log.debug("Tracking vehicle click - User: {}, Vehicle: {}, Context: {}", 
                    userId, vehicleId, context);
            
            behaviorTrackingService.trackVehicleClick(userId, vehicleId, context);
            
            return ResponseEntity.ok(ApiResponse.success("Click tracked", 
                    "Vehicle click has been recorded for analytics"));

        } catch (Exception e) {
            log.error("Error tracking vehicle click: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to track click: " + e.getMessage()));
        }
    }

    /**
     * Track user behavior - favorite action
     */
    @PostMapping("/behavior/track/favorite")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Track favorite action", 
               description = "Track user favorite/unfavorite actions for preference learning")
    public ResponseEntity<ApiResponse<String>> trackVehicleFavorite(
            @Parameter(description = "User ID") @RequestParam Long userId,
            @Parameter(description = "Vehicle ID") @RequestParam Long vehicleId,
            @Parameter(description = "Is favorite") @RequestParam boolean isFavorite) {
        
        try {
            log.debug("Tracking favorite action - User: {}, Vehicle: {}, Favorite: {}", 
                    userId, vehicleId, isFavorite);
            
            behaviorTrackingService.trackVehicleFavorite(userId, vehicleId, isFavorite);
            
            return ResponseEntity.ok(ApiResponse.success("Favorite tracked", 
                    "Favorite action has been recorded for preference learning"));

        } catch (Exception e) {
            log.error("Error tracking favorite action: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to track favorite: " + e.getMessage()));
        }
    }

    /**
     * Track user behavior - vehicle inquiry
     */
    @PostMapping("/behavior/track/inquiry")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Track vehicle inquiry", 
               description = "Track user inquiries about vehicles for strong preference signals")
    public ResponseEntity<ApiResponse<String>> trackVehicleInquiry(
            @Parameter(description = "User ID") @RequestParam Long userId,
            @Parameter(description = "Vehicle ID") @RequestParam Long vehicleId,
            @Parameter(description = "Inquiry type") @RequestParam String inquiryType) {
        
        try {
            log.debug("Tracking vehicle inquiry - User: {}, Vehicle: {}, Type: {}", 
                    userId, vehicleId, inquiryType);
            
            behaviorTrackingService.trackVehicleInquiry(userId, vehicleId, inquiryType);
            
            return ResponseEntity.ok(ApiResponse.success("Inquiry tracked", 
                    "Vehicle inquiry has been recorded for preference learning"));

        } catch (Exception e) {
            log.error("Error tracking vehicle inquiry: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to track inquiry: " + e.getMessage()));
        }
    }

    /**
     * Get user analytics and behavior insights
     */
    @GetMapping("/analytics/user/{userId}")
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal.id or hasRole('ADMIN')")
    @Operation(summary = "Get user analytics", 
               description = "Get comprehensive user behavior analytics and insights")
    public ResponseEntity<ApiResponse<UserAnalytics>> getUserAnalytics(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        
        try {
            log.info("Getting user analytics for user: {}", userId);
            
            UserAnalytics analytics = behaviorTrackingService.getUserAnalytics(userId);
            
            return ResponseEntity.ok(ApiResponse.success(analytics, 
                    "User analytics retrieved successfully"));

        } catch (Exception e) {
            log.error("Error getting user analytics for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get user analytics: " + e.getMessage()));
        }
    }

    /**
     * Get user's search history
     */
    @GetMapping("/analytics/search-history/{userId}")
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal.id or hasRole('ADMIN')")
    @Operation(summary = "Get search history", 
               description = "Get user's search history for personalization")
    public ResponseEntity<ApiResponse<List<String>>> getUserSearchHistory(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        
        try {
            log.info("Getting search history for user: {}", userId);
            
            List<String> searchHistory = behaviorTrackingService.getSearchHistory(userId);
            
            return ResponseEntity.ok(ApiResponse.success(searchHistory, 
                    "Search history retrieved successfully"));

        } catch (Exception e) {
            log.error("Error getting search history for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get search history: " + e.getMessage()));
        }
    }

    /**
     * Get user's view history
     */
    @GetMapping("/analytics/view-history/{userId}")
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal.id or hasRole('ADMIN')")
    @Operation(summary = "Get view history", 
               description = "Get user's vehicle viewing history")
    public ResponseEntity<ApiResponse<List<Vehicle>>> getUserViewHistory(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        
        try {
            log.info("Getting view history for user: {}", userId);
            
            List<Vehicle> viewHistory = behaviorTrackingService.getViewHistory(userId);
            
            return ResponseEntity.ok(ApiResponse.success(viewHistory, 
                    "View history retrieved successfully"));

        } catch (Exception e) {
            log.error("Error getting view history for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get view history: " + e.getMessage()));
        }
    }

    /**
     * Get user's favorite vehicles
     */
    @GetMapping("/analytics/favorites/{userId}")
    @PreAuthorize("hasRole('USER') and #userId == authentication.principal.id or hasRole('ADMIN')")
    @Operation(summary = "Get favorite vehicles", 
               description = "Get user's favorite vehicles for preference analysis")
    public ResponseEntity<ApiResponse<List<Vehicle>>> getUserFavorites(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        
        try {
            log.info("Getting favorite vehicles for user: {}", userId);
            
            List<Vehicle> favorites = behaviorTrackingService.getFavoriteVehicles(userId);
            
            return ResponseEntity.ok(ApiResponse.success(favorites, 
                    "Favorite vehicles retrieved successfully"));

        } catch (Exception e) {
            log.error("Error getting favorites for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get favorites: " + e.getMessage()));
        }
    }

    /**
     * Get trending search terms globally
     */
    @GetMapping("/analytics/trending-searches")
    @Operation(summary = "Get trending searches", 
               description = "Get globally trending search terms")
    public ResponseEntity<ApiResponse<List<String>>> getTrendingSearchTerms(
            @Parameter(description = "Maximum results") @RequestParam(required = false, defaultValue = "10") int limit) {
        
        try {
            log.info("Getting trending search terms");
            
            List<String> trendingTerms = behaviorTrackingService.getTrendingSearchTerms(limit);
            
            return ResponseEntity.ok(ApiResponse.success(trendingTerms, 
                    "Trending search terms retrieved successfully"));

        } catch (Exception e) {
            log.error("Error getting trending search terms: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to get trending terms: " + e.getMessage()));
        }
    }

    /**
     * Find users with similar behavior (for collaborative filtering)
     */
    @GetMapping("/analytics/similar-users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get similar users", 
               description = "Find users with similar behavior patterns (Admin only)")
    public ResponseEntity<ApiResponse<List<Long>>> getSimilarUsers(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "Maximum results") @RequestParam(required = false, defaultValue = "10") int limit) {
        
        try {
            log.info("Finding similar users for user: {}", userId);
            
            List<Long> similarUsers = behaviorTrackingService.findSimilarUsers(userId, limit);
            
            return ResponseEntity.ok(ApiResponse.success(similarUsers, 
                    "Similar users retrieved successfully"));

        } catch (Exception e) {
            log.error("Error finding similar users for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to find similar users: " + e.getMessage()));
        }
    }

    /**
     * ML Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "ML service health", 
               description = "Check the health and status of ML services")
    public ResponseEntity<ApiResponse<Map<String, Object>>> mlHealthCheck() {
        
        try {
            Map<String, Object> healthInfo = Map.of(
                "status", "healthy",
                "timestamp", System.currentTimeMillis(),
                "services", List.of(
                    "recommendation-engine",
                    "price-prediction", 
                    "behavior-tracking",
                    "user-analytics"
                ),
                "features", List.of(
                    "personalized-recommendations",
                    "similar-vehicle-search",
                    "ai-price-prediction",
                    "market-trend-analysis",
                    "user-behavior-tracking",
                    "collaborative-filtering",
                    "content-based-filtering"
                ),
                "version", "1.0.0"
            );
            
            return ResponseEntity.ok(ApiResponse.success(healthInfo, 
                    "ML services are healthy and operational"));

        } catch (Exception e) {
            log.error("ML health check failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("ML service health check failed: " + e.getMessage()));
        }
    }
}
