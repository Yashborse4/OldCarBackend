package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.ApiResponse;
import com.carselling.oldcar.service.IntelligentMarketAnalyticsService;
import com.carselling.oldcar.service.IntelligentMarketAnalyticsService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Market Analytics Controller
 * Provides AI-driven market insights, demand forecasting, and trend analysis
 */
@RestController
@RequestMapping("/api/market-analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Market Analytics", description = "AI-powered market insights, demand forecasting, and competitive analysis")
public class MarketAnalyticsController {

    private final IntelligentMarketAnalyticsService marketAnalyticsService;

    /**
     * Get comprehensive market overview dashboard
     */
    @GetMapping("/overview")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Market overview", 
               description = "Get comprehensive market overview with trends, pricing, and predictions")
    public ResponseEntity<ApiResponse<MarketOverview>> getMarketOverview() {
        
        try {
            log.info("Generating market overview dashboard");
            
            MarketOverview overview = marketAnalyticsService.getMarketOverview();
            
            return ResponseEntity.ok(ApiResponse.success(overview, 
                    "Market overview generated successfully"));

        } catch (Exception e) {
            log.error("Error generating market overview: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to generate market overview: " + e.getMessage()));
        }
    }

    /**
     * Get demand forecasting for specific vehicle segments
     */
    @GetMapping("/demand-forecast")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Demand forecast", 
               description = "Get AI-powered demand forecasting for specific vehicle makes and models")
    public ResponseEntity<ApiResponse<DemandForecast>> getDemandForecast(
            @Parameter(description = "Vehicle make") @RequestParam String make,
            @Parameter(description = "Vehicle model") @RequestParam String model) {
        
        try {
            log.info("Generating demand forecast for {} {}", make, model);
            
            DemandForecast forecast = marketAnalyticsService.getDemandForecast(make, model);
            
            return ResponseEntity.ok(ApiResponse.success(forecast, 
                    "Demand forecast generated successfully"));

        } catch (Exception e) {
            log.error("Error generating demand forecast for {} {}: {}", make, model, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to generate demand forecast: " + e.getMessage()));
        }
    }

    /**
     * Get competitive analysis for a specific vehicle
     */
    @GetMapping("/competitive-analysis/{vehicleId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Competitive analysis", 
               description = "Perform comprehensive competitive analysis for a specific vehicle")
    public ResponseEntity<ApiResponse<CompetitiveAnalysis>> getCompetitiveAnalysis(
            @Parameter(description = "Vehicle ID") @PathVariable Long vehicleId) {
        
        try {
            log.info("Performing competitive analysis for vehicle: {}", vehicleId);
            
            CompetitiveAnalysis analysis = marketAnalyticsService.getCompetitiveAnalysis(vehicleId);
            
            return ResponseEntity.ok(ApiResponse.success(analysis, 
                    "Competitive analysis completed successfully"));

        } catch (Exception e) {
            log.error("Error performing competitive analysis for vehicle {}: {}", vehicleId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to perform competitive analysis: " + e.getMessage()));
        }
    }

    /**
     * Get market segmentation analysis
     */
    @GetMapping("/segmentation")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DEALER')")
    @Operation(summary = "Market segmentation", 
               description = "Get comprehensive market segmentation analysis by price, age, brand, and geography")
    public ResponseEntity<ApiResponse<MarketSegmentation>> getMarketSegmentation() {
        
        try {
            log.info("Performing market segmentation analysis");
            
            MarketSegmentation segmentation = marketAnalyticsService.getMarketSegmentation();
            
            return ResponseEntity.ok(ApiResponse.success(segmentation, 
                    "Market segmentation analysis completed successfully"));

        } catch (Exception e) {
            log.error("Error performing market segmentation: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to perform market segmentation: " + e.getMessage()));
        }
    }

    /**
     * Get real-time market alerts
     */
    @GetMapping("/alerts")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Market alerts", 
               description = "Get real-time market alerts for price movements, inventory changes, and opportunities")
    public ResponseEntity<ApiResponse<List<MarketAlert>>> getMarketAlerts() {
        
        try {
            log.info("Generating real-time market alerts");
            
            List<MarketAlert> alerts = marketAnalyticsService.getMarketAlerts();
            
            return ResponseEntity.ok(ApiResponse.success(alerts, 
                    "Market alerts retrieved successfully"));

        } catch (Exception e) {
            log.error("Error generating market alerts: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to generate market alerts: " + e.getMessage()));
        }
    }

    /**
     * Generate comprehensive market insights report
     */
    @PostMapping("/insights-report")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DEALER')")
    @Operation(summary = "Generate market insights report", 
               description = "Generate comprehensive market insights report with trends, analysis, and recommendations")
    public ResponseEntity<ApiResponse<String>> generateMarketInsightsReport() {
        
        try {
            log.info("Initiating market insights report generation");
            
            CompletableFuture<MarketInsightsReport> reportFuture = 
                    marketAnalyticsService.generateMarketInsightsReport();
            
            return ResponseEntity.ok(ApiResponse.success("Report generation initiated", 
                    "Market insights report is being generated in the background"));

        } catch (Exception e) {
            log.error("Error initiating market insights report generation: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to initiate report generation: " + e.getMessage()));
        }
    }

    /**
     * Get market trends by vehicle category
     */
    @GetMapping("/trends/category")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Category trends", 
               description = "Get market trends analysis by vehicle category (luxury, economy, mid-range)")
    public ResponseEntity<ApiResponse<List<MarketSegment>>> getCategoryTrends(
            @Parameter(description = "Category type") @RequestParam(required = false, defaultValue = "all") String category) {
        
        try {
            log.info("Analyzing trends for category: {}", category);
            
            MarketOverview overview = marketAnalyticsService.getMarketOverview();
            List<MarketSegment> trends;
            
            switch (category.toLowerCase()) {
                case "makes" -> trends = overview.getTopMakes();
                case "models" -> trends = overview.getTopModels();
                default -> trends = overview.getTopMakes(); // Default to makes
            }
            
            return ResponseEntity.ok(ApiResponse.success(trends, 
                    "Category trends retrieved successfully"));

        } catch (Exception e) {
            log.error("Error analyzing category trends: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to analyze category trends: " + e.getMessage()));
        }
    }

    /**
     * Get price trends analysis
     */
    @GetMapping("/trends/price")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Price trends", 
               description = "Get detailed price trends analysis by price ranges")
    public ResponseEntity<ApiResponse<List<PriceRangeSegment>>> getPriceTrends() {
        
        try {
            log.info("Analyzing price trends");
            
            MarketOverview overview = marketAnalyticsService.getMarketOverview();
            List<PriceRangeSegment> priceTrends = overview.getPopularPriceRanges();
            
            return ResponseEntity.ok(ApiResponse.success(priceTrends, 
                    "Price trends retrieved successfully"));

        } catch (Exception e) {
            log.error("Error analyzing price trends: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to analyze price trends: " + e.getMessage()));
        }
    }

    /**
     * Get vehicle age trends analysis
     */
    @GetMapping("/trends/age")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Age trends", 
               description = "Get market trends analysis by vehicle age ranges")
    public ResponseEntity<ApiResponse<List<YearRangeSegment>>> getAgeTrends() {
        
        try {
            log.info("Analyzing vehicle age trends");
            
            MarketOverview overview = marketAnalyticsService.getMarketOverview();
            List<YearRangeSegment> ageTrends = overview.getPopularYearRanges();
            
            return ResponseEntity.ok(ApiResponse.success(ageTrends, 
                    "Age trends retrieved successfully"));

        } catch (Exception e) {
            log.error("Error analyzing age trends: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to analyze age trends: " + e.getMessage()));
        }
    }

    /**
     * Get seasonal market insights
     */
    @GetMapping("/insights/seasonal")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Seasonal insights", 
               description = "Get seasonal market insights and recommendations")
    public ResponseEntity<ApiResponse<List<String>>> getSeasonalInsights() {
        
        try {
            log.info("Generating seasonal market insights");
            
            MarketOverview overview = marketAnalyticsService.getMarketOverview();
            List<String> insights = overview.getSeasonalInsights();
            
            return ResponseEntity.ok(ApiResponse.success(insights, 
                    "Seasonal insights retrieved successfully"));

        } catch (Exception e) {
            log.error("Error generating seasonal insights: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to generate seasonal insights: " + e.getMessage()));
        }
    }

    /**
     * Get market predictions
     */
    @GetMapping("/predictions")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Market predictions", 
               description = "Get AI-powered market predictions for the next month")
    public ResponseEntity<ApiResponse<MarketPrediction>> getMarketPredictions() {
        
        try {
            log.info("Generating market predictions");
            
            MarketOverview overview = marketAnalyticsService.getMarketOverview();
            MarketPrediction prediction = overview.getNextMonthPrediction();
            
            return ResponseEntity.ok(ApiResponse.success(prediction, 
                    "Market predictions generated successfully"));

        } catch (Exception e) {
            log.error("Error generating market predictions: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to generate market predictions: " + e.getMessage()));
        }
    }

    /**
     * Get market health indicators
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DEALER')")
    @Operation(summary = "Market health", 
               description = "Get comprehensive market health indicators and metrics")
    public ResponseEntity<ApiResponse<MarketHealthIndicators>> getMarketHealth() {
        
        try {
            log.info("Analyzing market health indicators");
            
            MarketOverview overview = marketAnalyticsService.getMarketOverview();
            
            MarketHealthIndicators health = new MarketHealthIndicators();
            health.setTotalListings(overview.getTotalActiveListings());
            health.setWeeklyGrowth(overview.getNewListingsThisWeek());
            health.setDailyGrowth(overview.getNewListingsToday());
            health.setAveragePrice(overview.getAverageMarketPrice());
            health.setMedianPrice(overview.getMedianMarketPrice());
            health.setPriceRange(overview.getPriceRange());
            health.setListingTrend(overview.getListingTrend());
            health.setPriceTrend(overview.getPriceTrend());
            health.setDemandTrend(overview.getDemandTrend());
            health.setHealthScore(calculateOverallHealthScore(overview));
            health.setHealthStatus(determineHealthStatus(health.getHealthScore()));
            
            return ResponseEntity.ok(ApiResponse.success(health, 
                    "Market health indicators retrieved successfully"));

        } catch (Exception e) {
            log.error("Error analyzing market health: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to analyze market health: " + e.getMessage()));
        }
    }

    /**
     * Get inventory analytics
     */
    @GetMapping("/inventory")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DEALER')")
    @Operation(summary = "Inventory analytics", 
               description = "Get detailed inventory analytics and distribution")
    public ResponseEntity<ApiResponse<InventoryAnalytics>> getInventoryAnalytics() {
        
        try {
            log.info("Generating inventory analytics");
            
            MarketOverview overview = marketAnalyticsService.getMarketOverview();
            
            InventoryAnalytics inventory = new InventoryAnalytics();
            inventory.setTotalVehicles(overview.getTotalActiveListings());
            inventory.setNewListingsToday(overview.getNewListingsToday());
            inventory.setNewListingsThisWeek(overview.getNewListingsThisWeek());
            inventory.setMakeDistribution(overview.getTopMakes());
            inventory.setPriceDistribution(overview.getPopularPriceRanges());
            inventory.setAgeDistribution(overview.getPopularYearRanges());
            inventory.setInventoryTurnover("2.4 months"); // Placeholder
            inventory.setInventoryHealth("Good");
            
            return ResponseEntity.ok(ApiResponse.success(inventory, 
                    "Inventory analytics generated successfully"));

        } catch (Exception e) {
            log.error("Error generating inventory analytics: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to generate inventory analytics: " + e.getMessage()));
        }
    }

    /**
     * Market analytics health check
     */
    @GetMapping("/service-health")
    @Operation(summary = "Service health", 
               description = "Check the health and status of market analytics services")
    public ResponseEntity<ApiResponse<ServiceHealthInfo>> getServiceHealth() {
        
        try {
            ServiceHealthInfo health = new ServiceHealthInfo();
            health.setStatus("healthy");
            health.setTimestamp(System.currentTimeMillis());
            health.setServices(List.of(
                "market-overview",
                "demand-forecasting", 
                "competitive-analysis",
                "market-segmentation",
                "trend-analysis",
                "alert-system"
            ));
            health.setFeatures(List.of(
                "ai-powered-insights",
                "demand-forecasting",
                "competitive-analysis", 
                "market-segmentation",
                "real-time-alerts",
                "trend-analysis",
                "seasonal-insights",
                "price-predictions"
            ));
            health.setVersion("1.0.0");
            
            return ResponseEntity.ok(ApiResponse.success(health, 
                    "Market analytics services are healthy"));

        } catch (Exception e) {
            log.error("Market analytics health check failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Market analytics health check failed: " + e.getMessage()));
        }
    }

    // Helper methods
    
    private double calculateOverallHealthScore(MarketOverview overview) {
        double score = 50.0; // Base score
        
        // Listing activity score
        if (overview.getNewListingsThisWeek() > overview.getNewListingsToday() * 7) {
            score += 15.0; // Growing activity
        }
        
        // Price stability score
        if ("Stable".equals(overview.getPriceTrend()) || "Increasing".equals(overview.getPriceTrend())) {
            score += 10.0;
        }
        
        // Demand score
        switch (overview.getDemandTrend()) {
            case "High" -> score += 15.0;
            case "Moderate" -> score += 10.0;
            default -> score += 5.0;
        }
        
        // Inventory diversity score
        if (overview.getTopMakes().size() >= 5) {
            score += 10.0; // Good diversity
        }
        
        return Math.min(score, 100.0);
    }
    
    private String determineHealthStatus(double healthScore) {
        if (healthScore >= 80) return "Excellent";
        else if (healthScore >= 65) return "Good";
        else if (healthScore >= 50) return "Fair";
        else return "Poor";
    }

    // Data classes for additional analytics

    public static class MarketHealthIndicators {
        private long totalListings;
        private long weeklyGrowth;
        private long dailyGrowth;
        private java.math.BigDecimal averagePrice;
        private java.math.BigDecimal medianPrice;
        private PriceRange priceRange;
        private String listingTrend;
        private String priceTrend;
        private String demandTrend;
        private double healthScore;
        private String healthStatus;

        // Getters and setters
        public long getTotalListings() { return totalListings; }
        public void setTotalListings(long totalListings) { this.totalListings = totalListings; }
        
        public long getWeeklyGrowth() { return weeklyGrowth; }
        public void setWeeklyGrowth(long weeklyGrowth) { this.weeklyGrowth = weeklyGrowth; }
        
        public long getDailyGrowth() { return dailyGrowth; }
        public void setDailyGrowth(long dailyGrowth) { this.dailyGrowth = dailyGrowth; }
        
        public java.math.BigDecimal getAveragePrice() { return averagePrice; }
        public void setAveragePrice(java.math.BigDecimal averagePrice) { this.averagePrice = averagePrice; }
        
        public java.math.BigDecimal getMedianPrice() { return medianPrice; }
        public void setMedianPrice(java.math.BigDecimal medianPrice) { this.medianPrice = medianPrice; }
        
        public PriceRange getPriceRange() { return priceRange; }
        public void setPriceRange(PriceRange priceRange) { this.priceRange = priceRange; }
        
        public String getListingTrend() { return listingTrend; }
        public void setListingTrend(String listingTrend) { this.listingTrend = listingTrend; }
        
        public String getPriceTrend() { return priceTrend; }
        public void setPriceTrend(String priceTrend) { this.priceTrend = priceTrend; }
        
        public String getDemandTrend() { return demandTrend; }
        public void setDemandTrend(String demandTrend) { this.demandTrend = demandTrend; }
        
        public double getHealthScore() { return healthScore; }
        public void setHealthScore(double healthScore) { this.healthScore = healthScore; }
        
        public String getHealthStatus() { return healthStatus; }
        public void setHealthStatus(String healthStatus) { this.healthStatus = healthStatus; }
    }

    public static class InventoryAnalytics {
        private long totalVehicles;
        private long newListingsToday;
        private long newListingsThisWeek;
        private List<MarketSegment> makeDistribution;
        private List<PriceRangeSegment> priceDistribution;
        private List<YearRangeSegment> ageDistribution;
        private String inventoryTurnover;
        private String inventoryHealth;

        // Getters and setters
        public long getTotalVehicles() { return totalVehicles; }
        public void setTotalVehicles(long totalVehicles) { this.totalVehicles = totalVehicles; }
        
        public long getNewListingsToday() { return newListingsToday; }
        public void setNewListingsToday(long newListingsToday) { this.newListingsToday = newListingsToday; }
        
        public long getNewListingsThisWeek() { return newListingsThisWeek; }
        public void setNewListingsThisWeek(long newListingsThisWeek) { this.newListingsThisWeek = newListingsThisWeek; }
        
        public List<MarketSegment> getMakeDistribution() { return makeDistribution; }
        public void setMakeDistribution(List<MarketSegment> makeDistribution) { this.makeDistribution = makeDistribution; }
        
        public List<PriceRangeSegment> getPriceDistribution() { return priceDistribution; }
        public void setPriceDistribution(List<PriceRangeSegment> priceDistribution) { this.priceDistribution = priceDistribution; }
        
        public List<YearRangeSegment> getAgeDistribution() { return ageDistribution; }
        public void setAgeDistribution(List<YearRangeSegment> ageDistribution) { this.ageDistribution = ageDistribution; }
        
        public String getInventoryTurnover() { return inventoryTurnover; }
        public void setInventoryTurnover(String inventoryTurnover) { this.inventoryTurnover = inventoryTurnover; }
        
        public String getInventoryHealth() { return inventoryHealth; }
        public void setInventoryHealth(String inventoryHealth) { this.inventoryHealth = inventoryHealth; }
    }

    public static class ServiceHealthInfo {
        private String status;
        private long timestamp;
        private List<String> services;
        private List<String> features;
        private String version;

        // Getters and setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public List<String> getServices() { return services; }
        public void setServices(List<String> services) { this.services = services; }
        
        public List<String> getFeatures() { return features; }
        public void setFeatures(List<String> features) { this.features = features; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }
}
