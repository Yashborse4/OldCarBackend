package com.carselling.oldcar.service;

import com.carselling.oldcar.entity.Vehicle;
import com.carselling.oldcar.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI-Powered Price Prediction Service
 * Uses machine learning algorithms to predict vehicle market values
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PricePredictionService {

    private final VehicleRepository vehicleRepository;
    // private final PerformanceMonitoringService performanceService; // Disabled

    // Market data constants (would typically come from external APIs)
    private static final Map<String, Double> BRAND_PREMIUM_FACTORS = Map.of(
        "BMW", 1.15, "Mercedes-Benz", 1.20, "Audi", 1.12, "Lexus", 1.18,
        "Toyota", 1.02, "Honda", 1.01, "Ford", 0.95, "Chevrolet", 0.93,
        "Hyundai", 0.90, "Kia", 0.88, "Nissan", 0.96, "Mazda", 0.94
    );

    private static final Map<String, Double> FUEL_TYPE_FACTORS = Map.of(
        "ELECTRIC", 1.08, "HYBRID", 1.05, "DIESEL", 1.02, "PETROL", 1.0, "CNG", 0.95
    );

    private static final Map<String, Double> TRANSMISSION_FACTORS = Map.of(
        "AUTOMATIC", 1.03, "MANUAL", 1.0, "CVT", 1.01
    );

    /**
     * Predict vehicle price using AI-powered algorithms
     */
    @Cacheable(value = "pricePredictions", key = "#vehicle.id")
    public double predictPrice(Vehicle vehicle) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Predicting price for vehicle: {} {} {}", 
                    vehicle.getMake(), vehicle.getModel(), vehicle.getYear());

            // Get base price using multiple algorithms
            double basePrice = calculateBasePrice(vehicle);
            double marketPrice = calculateMarketAdjustedPrice(vehicle, basePrice);
            double finalPrice = applyAdvancedFactors(vehicle, marketPrice);

            long duration = System.currentTimeMillis() - startTime;
            performanceService.recordApiCall("/ai/price-prediction", duration, true);

            log.debug("Predicted price for vehicle {}: ${}", vehicle.getId(), finalPrice);
            return finalPrice;

        } catch (Exception e) {
            log.error("Error predicting price for vehicle {}: {}", vehicle.getId(), e.getMessage(), e);
            performanceService.recordApiCall("/ai/price-prediction", 
                    System.currentTimeMillis() - startTime, false);
            // Fallback to simple depreciation model
            return calculateSimpleDepreciation(vehicle);
        }
    }

    /**
     * Get comprehensive price analysis for a vehicle
     */
    @Cacheable(value = "priceAnalysis", key = "#vehicle.id")
    public PriceAnalysis analyzePriceComprehensively(Vehicle vehicle) {
        try {
            log.info("Performing comprehensive price analysis for vehicle: {}", vehicle.getId());

            PriceAnalysis analysis = new PriceAnalysis();
            analysis.setVehicleId(vehicle.getId());
            analysis.setCurrentListingPrice(vehicle.getPrice().doubleValue());
            
            // AI-powered predictions
            analysis.setPredictedMarketValue(predictPrice(vehicle));
            analysis.setEstimatedMinPrice(analysis.getPredictedMarketValue() * 0.85);
            analysis.setEstimatedMaxPrice(analysis.getPredictedMarketValue() * 1.15);
            
            // Market analysis
            analysis.setPricePositioning(calculatePricePositioning(vehicle));
            analysis.setMarketCompetitiveness(calculateMarketCompetitiveness(vehicle));
            analysis.setValueRating(calculateValueRating(analysis));
            
            // Historical trends
            analysis.setDepreciationRate(calculateDepreciationRate(vehicle));
            analysis.setAppreciationPotential(calculateAppreciationPotential(vehicle));
            
            // Recommendations
            analysis.setRecommendations(generatePriceRecommendations(analysis, vehicle));
            analysis.setConfidenceScore(calculatePredictionConfidence(vehicle));
            
            analysis.setAnalysisDate(LocalDateTime.now());
            
            return analysis;
            
        } catch (Exception e) {
            log.error("Error performing price analysis for vehicle {}: {}", vehicle.getId(), e.getMessage(), e);
            return createFallbackAnalysis(vehicle);
        }
    }

    /**
     * Get market price trends for similar vehicles
     */
    public MarketTrendAnalysis getMarketTrends(String make, String model, int year, int months) {
        try {
            log.info("Analyzing market trends for {} {} {}", make, model, year);

            MarketTrendAnalysis trends = new MarketTrendAnalysis();
            trends.setMake(make);
            trends.setModel(model);
            trends.setYear(year);
            trends.setAnalysisPeriodMonths(months);

            // Get similar vehicles for trend analysis
            List<Vehicle> similarVehicles = vehicleRepository.findSimilarVehiclesForTrends(
                make, model, year - 1, year + 1);

            if (similarVehicles.isEmpty()) {
                return createDefaultTrends(make, model, year);
            }

            // Calculate price trends over time
            trends.setAveragePrice(calculateAveragePrice(similarVehicles));
            trends.setPriceRange(calculatePriceRange(similarVehicles));
            trends.setTrendDirection(calculateTrendDirection(similarVehicles));
            trends.setVolatility(calculatePriceVolatility(similarVehicles));
            trends.setMarketSupply(similarVehicles.size());
            
            // Market insights
            trends.setSeasonalFactors(calculateSeasonalFactors(make, model));
            trends.setDemandIndicator(calculateDemandIndicator(similarVehicles));
            trends.setLiquidityScore(calculateLiquidityScore(similarVehicles));

            return trends;

        } catch (Exception e) {
            log.error("Error analyzing market trends: {}", e.getMessage(), e);
            return createDefaultTrends(make, model, year);
        }
    }

    /**
     * Batch predict prices for multiple vehicles asynchronously
     */
    @Async
    public CompletableFuture<Map<Long, Double>> batchPredictPrices(List<Vehicle> vehicles) {
        try {
            log.info("Batch predicting prices for {} vehicles", vehicles.size());

            Map<Long, Double> predictions = vehicles.parallelStream()
                    .collect(Collectors.toConcurrentMap(
                        Vehicle::getId,
                        this::predictPrice
                    ));

            log.info("Completed batch price predictions for {} vehicles", predictions.size());
            return CompletableFuture.completedFuture(predictions);

        } catch (Exception e) {
            log.error("Error in batch price prediction: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }
    }

    // Private helper methods

    private double calculateBasePrice(Vehicle vehicle) {
        // Multi-factor base price calculation
        double basePrice = 0.0;

        // MSRP estimation based on year and segment
        double estimatedMSRP = estimateMSRP(vehicle.getMake(), vehicle.getModel(), vehicle.getYear());
        
        // Age depreciation (primary factor)
        double ageDepreciation = calculateAgeDepreciation(vehicle.getYear(), estimatedMSRP);
        basePrice += ageDepreciation;

        // Mileage depreciation
        double mileageDepreciation = calculateMileageDepreciation(vehicle.getMileage(), ageDepreciation);
        basePrice = basePrice - mileageDepreciation;

        // Brand premium/discount
        double brandFactor = BRAND_PREMIUM_FACTORS.getOrDefault(vehicle.getMake(), 1.0);
        basePrice = basePrice * brandFactor;

        return Math.max(basePrice, estimatedMSRP * 0.1); // Minimum 10% of MSRP
    }

    private double calculateMarketAdjustedPrice(Vehicle vehicle, double basePrice) {
        // Adjust for current market conditions
        double adjustedPrice = basePrice;

        // Similar vehicle pricing analysis
        List<Vehicle> comparables = findComparableVehicles(vehicle);
        if (!comparables.isEmpty()) {
            double marketAverage = comparables.stream()
                    .mapToDouble(v -> v.getPrice().doubleValue())
                    .average()
                    .orElse(basePrice);
            
            // Blend base price with market average (70% base, 30% market)
            adjustedPrice = (basePrice * 0.7) + (marketAverage * 0.3);
        }

        // Apply fuel type factors
        double fuelFactor = FUEL_TYPE_FACTORS.getOrDefault(vehicle.getFuelType(), 1.0);
        adjustedPrice = adjustedPrice * fuelFactor;

        // Apply transmission factors
        double transmissionFactor = TRANSMISSION_FACTORS.getOrDefault(vehicle.getTransmission(), 1.0);
        adjustedPrice = adjustedPrice * transmissionFactor;

        return adjustedPrice;
    }

    private double applyAdvancedFactors(Vehicle vehicle, double marketPrice) {
        double finalPrice = marketPrice;

        // Condition factor (would come from AI image analysis)
        double conditionFactor = estimateConditionFactor(vehicle);
        finalPrice = finalPrice * conditionFactor;

        // Location factor
        double locationFactor = calculateLocationFactor(vehicle.getLocation());
        finalPrice = finalPrice * locationFactor;

        // Seasonal adjustment
        double seasonalFactor = calculateSeasonalAdjustment(vehicle);
        finalPrice = finalPrice * seasonalFactor;

        // Supply and demand adjustment
        double supplyDemandFactor = calculateSupplyDemandFactor(vehicle);
        finalPrice = finalPrice * supplyDemandFactor;

        return Math.round(finalPrice * 100.0) / 100.0; // Round to 2 decimal places
    }

    private double estimateMSRP(String make, String model, int year) {
        // Simplified MSRP estimation - would use external API in production
        Map<String, Double> basePrices = Map.of(
            "Toyota", 25000.0, "Honda", 24000.0, "BMW", 45000.0,
            "Mercedes-Benz", 50000.0, "Audi", 42000.0, "Ford", 28000.0,
            "Chevrolet", 26000.0, "Hyundai", 22000.0, "Nissan", 25000.0
        );

        double basePrice = basePrices.getOrDefault(make, 25000.0);
        
        // Adjust for year (assuming 3% annual increase)
        int currentYear = LocalDateTime.now().getYear();
        double yearAdjustment = Math.pow(1.03, currentYear - year);
        
        return basePrice * yearAdjustment;
    }

    private double calculateAgeDepreciation(int year, double msrp) {
        int currentYear = LocalDateTime.now().getYear();
        int age = currentYear - year;

        // Advanced depreciation curve (non-linear)
        double depreciationRate;
        if (age <= 1) depreciationRate = 0.80; // 20% first year
        else if (age <= 3) depreciationRate = 0.75 - (age * 0.05); // 5% per year
        else if (age <= 7) depreciationRate = 0.60 - (age * 0.03); // 3% per year
        else depreciationRate = Math.max(0.15, 0.60 - (age * 0.02)); // 2% per year, min 15%

        return msrp * depreciationRate;
    }

    private double calculateMileageDepreciation(long mileage, double baseValue) {
        // Standard depreciation: $0.10 per mile for first 100k, $0.05 after
        double mileageDepreciation = 0.0;
        
        if (mileage <= 100000) {
            mileageDepreciation = mileage * 0.10;
        } else {
            mileageDepreciation = (100000 * 0.10) + ((mileage - 100000) * 0.05);
        }

        return Math.min(mileageDepreciation, baseValue * 0.3); // Cap at 30% of base value
    }

    private List<Vehicle> findComparableVehicles(Vehicle target) {
        return vehicleRepository.findComparableVehicles(
            target.getMake(),
            target.getModel(),
            target.getYear() - 1,
            target.getYear() + 1,
            target.getMileage() * 0.8,
            target.getMileage() * 1.2,
            10 // Limit to 10 comparables
        );
    }

    private double estimateConditionFactor(Vehicle vehicle) {
        // Would integrate with AI image analysis
        // For now, assume good condition
        return 1.0;
    }

    private double calculateLocationFactor(String location) {
        // Different locations have different market values
        Map<String, Double> locationFactors = Map.of(
            "California", 1.1, "New York", 1.08, "Texas", 1.02,
            "Florida", 1.05, "Illinois", 1.01, "Ohio", 0.95,
            "Michigan", 0.92, "Pennsylvania", 0.98
        );

        return locationFactors.entrySet().stream()
                .filter(entry -> location != null && location.toLowerCase().contains(entry.getKey().toLowerCase()))
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(1.0);
    }

    private double calculateSeasonalAdjustment(Vehicle vehicle) {
        int month = LocalDateTime.now().getMonthValue();
        
        // Convertibles and sports cars sell better in spring/summer
        if (vehicle.getModel().toLowerCase().contains("convertible") ||
            vehicle.getModel().toLowerCase().contains("roadster")) {
            if (month >= 3 && month <= 8) return 1.05; // Spring/Summer boost
            else return 0.97; // Winter discount
        }
        
        // SUVs and trucks sell better before winter
        if (vehicle.getModel().toLowerCase().contains("suv") ||
            vehicle.getModel().toLowerCase().contains("truck")) {
            if (month >= 9 && month <= 11) return 1.03; // Fall boost
        }

        return 1.0; // No seasonal adjustment
    }

    private double calculateSupplyDemandFactor(Vehicle vehicle) {
        // Count similar vehicles in the market
        List<Vehicle> similarVehicles = vehicleRepository.findByMakeAndModel(
            vehicle.getMake(), vehicle.getModel());
        
        int supply = similarVehicles.size();
        
        // High supply = lower prices, Low supply = higher prices
        if (supply < 5) return 1.05; // Low supply boost
        else if (supply > 20) return 0.97; // High supply discount
        else return 1.0; // Normal market
    }

    private double calculateSimpleDepreciation(Vehicle vehicle) {
        // Fallback simple depreciation model
        int currentYear = LocalDateTime.now().getYear();
        int age = currentYear - vehicle.getYear();
        
        double baseValue = 30000.0; // Average vehicle value
        double depreciationRate = 0.85; // 15% per year
        
        return baseValue * Math.pow(depreciationRate, age);
    }

    private String calculatePricePositioning(Vehicle vehicle) {
        List<Vehicle> comparables = findComparableVehicles(vehicle);
        if (comparables.isEmpty()) return "No comparables available";

        double avgPrice = comparables.stream()
                .mapToDouble(v -> v.getPrice().doubleValue())
                .average()
                .orElse(vehicle.getPrice().doubleValue());

        double priceRatio = vehicle.getPrice().doubleValue() / avgPrice;

        if (priceRatio < 0.9) return "Below Market";
        else if (priceRatio > 1.1) return "Above Market";
        else return "At Market";
    }

    private double calculateMarketCompetitiveness(Vehicle vehicle) {
        double predictedPrice = predictPrice(vehicle);
        double listingPrice = vehicle.getPrice().doubleValue();
        
        // Competitiveness score: how well priced compared to predicted value
        double ratio = listingPrice / predictedPrice;
        
        if (ratio <= 0.9) return 90.0; // Excellent deal
        else if (ratio <= 0.95) return 80.0; // Good deal
        else if (ratio <= 1.05) return 70.0; // Fair price
        else if (ratio <= 1.1) return 60.0; // Slightly overpriced
        else return 40.0; // Overpriced
    }

    private String calculateValueRating(PriceAnalysis analysis) {
        double competitiveness = analysis.getMarketCompetitiveness();
        
        if (competitiveness >= 85) return "Excellent Value";
        else if (competitiveness >= 75) return "Good Value";
        else if (competitiveness >= 65) return "Fair Value";
        else if (competitiveness >= 55) return "Poor Value";
        else return "Overpriced";
    }

    private double calculateDepreciationRate(Vehicle vehicle) {
        int age = LocalDateTime.now().getYear() - vehicle.getYear();
        if (age == 0) return 15.0; // New cars depreciate 15% first year
        
        // Standard depreciation rates by age
        if (age <= 3) return 10.0; // 10% annually for first 3 years
        else if (age <= 7) return 6.0; // 6% annually for years 4-7
        else return 3.0; // 3% annually after 7 years
    }

    private double calculateAppreciationPotential(Vehicle vehicle) {
        // Classic car potential, rare models, etc.
        int age = LocalDateTime.now().getYear() - vehicle.getYear();
        
        // Very old cars might have classic potential
        if (age > 25) return 15.0; // 15% potential appreciation
        else if (age > 20) return 8.0; // 8% potential
        else if (vehicle.getMake().equals("Ferrari") || vehicle.getMake().equals("Porsche")) {
            return 12.0; // Luxury sports cars
        }
        else return 2.0; // Standard 2% potential
    }

    private List<String> generatePriceRecommendations(PriceAnalysis analysis, Vehicle vehicle) {
        List<String> recommendations = new ArrayList<>();
        
        double currentPrice = analysis.getCurrentListingPrice();
        double predictedPrice = analysis.getPredictedMarketValue();
        double ratio = currentPrice / predictedPrice;

        if (ratio > 1.1) {
            recommendations.add("Consider reducing price by " + 
                String.format("%.0f", (currentPrice - predictedPrice)) + 
                " to match market expectations");
        } else if (ratio < 0.9) {
            recommendations.add("Vehicle is priced below market value - could increase price by " +
                String.format("%.0f", (predictedPrice - currentPrice)));
        }

        if (analysis.getMarketCompetitiveness() < 60) {
            recommendations.add("Pricing is not competitive compared to similar vehicles");
        }

        if (analysis.getDepreciationRate() > 10) {
            recommendations.add("Consider selling soon as depreciation rate is high");
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Pricing is well-positioned for the current market");
        }

        return recommendations;
    }

    private double calculatePredictionConfidence(Vehicle vehicle) {
        List<Vehicle> comparables = findComparableVehicles(vehicle);
        
        // Confidence based on available market data
        if (comparables.size() >= 10) return 95.0; // High confidence
        else if (comparables.size() >= 5) return 80.0; // Good confidence
        else if (comparables.size() >= 2) return 65.0; // Moderate confidence
        else return 45.0; // Low confidence
    }

    private PriceAnalysis createFallbackAnalysis(Vehicle vehicle) {
        PriceAnalysis analysis = new PriceAnalysis();
        analysis.setVehicleId(vehicle.getId());
        analysis.setCurrentListingPrice(vehicle.getPrice().doubleValue());
        analysis.setPredictedMarketValue(calculateSimpleDepreciation(vehicle));
        analysis.setConfidenceScore(30.0); // Low confidence for fallback
        analysis.setValueRating("Analysis Unavailable");
        analysis.setRecommendations(List.of("Unable to perform comprehensive analysis"));
        analysis.setAnalysisDate(LocalDateTime.now());
        return analysis;
    }

    // Market trend analysis methods
    
    private double calculateAveragePrice(List<Vehicle> vehicles) {
        return vehicles.stream()
                .mapToDouble(v -> v.getPrice().doubleValue())
                .average()
                .orElse(0.0);
    }

    private PriceRange calculatePriceRange(List<Vehicle> vehicles) {
        DoubleSummaryStatistics stats = vehicles.stream()
                .mapToDouble(v -> v.getPrice().doubleValue())
                .summaryStatistics();
        
        return new PriceRange(stats.getMin(), stats.getMax());
    }

    private String calculateTrendDirection(List<Vehicle> vehicles) {
        // Simplified trend calculation based on recent listings
        LocalDateTime threeMonthsAgo = LocalDateTime.now().minusMonths(3);
        
        double recentAvg = vehicles.stream()
                .filter(v -> v.getCreatedAt().isAfter(threeMonthsAgo))
                .mapToDouble(v -> v.getPrice().doubleValue())
                .average()
                .orElse(0.0);
                
        double olderAvg = vehicles.stream()
                .filter(v -> v.getCreatedAt().isBefore(threeMonthsAgo))
                .mapToDouble(v -> v.getPrice().doubleValue())
                .average()
                .orElse(recentAvg);

        if (recentAvg > olderAvg * 1.05) return "Increasing";
        else if (recentAvg < olderAvg * 0.95) return "Decreasing";
        else return "Stable";
    }

    private double calculatePriceVolatility(List<Vehicle> vehicles) {
        if (vehicles.size() < 2) return 0.0;
        
        double[] prices = vehicles.stream()
                .mapToDouble(v -> v.getPrice().doubleValue())
                .toArray();
                
        double mean = Arrays.stream(prices).average().orElse(0.0);
        double variance = Arrays.stream(prices)
                .map(price -> Math.pow(price - mean, 2))
                .average()
                .orElse(0.0);
                
        return Math.sqrt(variance) / mean * 100; // Coefficient of variation as percentage
    }

    private Map<String, Double> calculateSeasonalFactors(String make, String model) {
        // Simplified seasonal factors
        Map<String, Double> factors = new HashMap<>();
        factors.put("Spring", 1.02);
        factors.put("Summer", 1.05);
        factors.put("Fall", 1.01);
        factors.put("Winter", 0.98);
        return factors;
    }

    private double calculateDemandIndicator(List<Vehicle> vehicles) {
        // Based on how quickly similar vehicles are being listed
        long recentListings = vehicles.stream()
                .mapToLong(v -> ChronoUnit.DAYS.between(v.getCreatedAt(), LocalDateTime.now()))
                .filter(days -> days <= 30)
                .count();
        
        if (recentListings > vehicles.size() * 0.5) return 80.0; // High demand
        else if (recentListings > vehicles.size() * 0.3) return 60.0; // Moderate demand
        else return 40.0; // Low demand
    }

    private double calculateLiquidityScore(List<Vehicle> vehicles) {
        // How quickly similar vehicles typically sell (simplified)
        return 75.0; // Placeholder - would need actual sales data
    }

    private MarketTrendAnalysis createDefaultTrends(String make, String model, int year) {
        MarketTrendAnalysis trends = new MarketTrendAnalysis();
        trends.setMake(make);
        trends.setModel(model);
        trends.setYear(year);
        trends.setAveragePrice(25000.0);
        trends.setPriceRange(new PriceRange(20000.0, 30000.0));
        trends.setTrendDirection("Stable");
        trends.setVolatility(10.0);
        trends.setMarketSupply(0);
        trends.setDemandIndicator(50.0);
        trends.setLiquidityScore(50.0);
        return trends;
    }

    // Data classes

    public static class PriceAnalysis {
        private Long vehicleId;
        private double currentListingPrice;
        private double predictedMarketValue;
        private double estimatedMinPrice;
        private double estimatedMaxPrice;
        private String pricePositioning;
        private double marketCompetitiveness;
        private String valueRating;
        private double depreciationRate;
        private double appreciationPotential;
        private List<String> recommendations;
        private double confidenceScore;
        private LocalDateTime analysisDate;

        // Getters and setters
        public Long getVehicleId() { return vehicleId; }
        public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
        
        public double getCurrentListingPrice() { return currentListingPrice; }
        public void setCurrentListingPrice(double currentListingPrice) { this.currentListingPrice = currentListingPrice; }
        
        public double getPredictedMarketValue() { return predictedMarketValue; }
        public void setPredictedMarketValue(double predictedMarketValue) { this.predictedMarketValue = predictedMarketValue; }
        
        public double getEstimatedMinPrice() { return estimatedMinPrice; }
        public void setEstimatedMinPrice(double estimatedMinPrice) { this.estimatedMinPrice = estimatedMinPrice; }
        
        public double getEstimatedMaxPrice() { return estimatedMaxPrice; }
        public void setEstimatedMaxPrice(double estimatedMaxPrice) { this.estimatedMaxPrice = estimatedMaxPrice; }
        
        public String getPricePositioning() { return pricePositioning; }
        public void setPricePositioning(String pricePositioning) { this.pricePositioning = pricePositioning; }
        
        public double getMarketCompetitiveness() { return marketCompetitiveness; }
        public void setMarketCompetitiveness(double marketCompetitiveness) { this.marketCompetitiveness = marketCompetitiveness; }
        
        public String getValueRating() { return valueRating; }
        public void setValueRating(String valueRating) { this.valueRating = valueRating; }
        
        public double getDepreciationRate() { return depreciationRate; }
        public void setDepreciationRate(double depreciationRate) { this.depreciationRate = depreciationRate; }
        
        public double getAppreciationPotential() { return appreciationPotential; }
        public void setAppreciationPotential(double appreciationPotential) { this.appreciationPotential = appreciationPotential; }
        
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
        
        public double getConfidenceScore() { return confidenceScore; }
        public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
        
        public LocalDateTime getAnalysisDate() { return analysisDate; }
        public void setAnalysisDate(LocalDateTime analysisDate) { this.analysisDate = analysisDate; }
    }

    public static class MarketTrendAnalysis {
        private String make;
        private String model;
        private int year;
        private int analysisPeriodMonths;
        private double averagePrice;
        private PriceRange priceRange;
        private String trendDirection;
        private double volatility;
        private int marketSupply;
        private Map<String, Double> seasonalFactors;
        private double demandIndicator;
        private double liquidityScore;

        // Getters and setters
        public String getMake() { return make; }
        public void setMake(String make) { this.make = make; }
        
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        
        public int getYear() { return year; }
        public void setYear(int year) { this.year = year; }
        
        public int getAnalysisPeriodMonths() { return analysisPeriodMonths; }
        public void setAnalysisPeriodMonths(int analysisPeriodMonths) { this.analysisPeriodMonths = analysisPeriodMonths; }
        
        public double getAveragePrice() { return averagePrice; }
        public void setAveragePrice(double averagePrice) { this.averagePrice = averagePrice; }
        
        public PriceRange getPriceRange() { return priceRange; }
        public void setPriceRange(PriceRange priceRange) { this.priceRange = priceRange; }
        
        public String getTrendDirection() { return trendDirection; }
        public void setTrendDirection(String trendDirection) { this.trendDirection = trendDirection; }
        
        public double getVolatility() { return volatility; }
        public void setVolatility(double volatility) { this.volatility = volatility; }
        
        public int getMarketSupply() { return marketSupply; }
        public void setMarketSupply(int marketSupply) { this.marketSupply = marketSupply; }
        
        public Map<String, Double> getSeasonalFactors() { return seasonalFactors; }
        public void setSeasonalFactors(Map<String, Double> seasonalFactors) { this.seasonalFactors = seasonalFactors; }
        
        public double getDemandIndicator() { return demandIndicator; }
        public void setDemandIndicator(double demandIndicator) { this.demandIndicator = demandIndicator; }
        
        public double getLiquidityScore() { return liquidityScore; }
        public void setLiquidityScore(double liquidityScore) { this.liquidityScore = liquidityScore; }
    }

    public static class PriceRange {
        private double min;
        private double max;

        public PriceRange(double min, double max) {
            this.min = min;
            this.max = max;
        }

        public double getMin() { return min; }
        public void setMin(double min) { this.min = min; }
        
        public double getMax() { return max; }
        public void setMax(double max) { this.max = max; }
    }
}
