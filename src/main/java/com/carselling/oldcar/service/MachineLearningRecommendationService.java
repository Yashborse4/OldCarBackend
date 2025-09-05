package com.carselling.oldcar.service;

import com.carselling.oldcar.entity.User;
import com.carselling.oldcar.entity.Vehicle;
import com.carselling.oldcar.repository.VehicleRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Advanced Machine Learning Recommendation Engine
 * Provides intelligent vehicle recommendations using multiple ML algorithms
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MachineLearningRecommendationService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final UserBehaviorTrackingService userBehaviorService;
    private final PricePredictionService pricePredictionService;
    private final PerformanceMonitoringService performanceService;

    /**
     * Get personalized vehicle recommendations using hybrid algorithm
     */
    @Cacheable(value = "mlRecommendations", key = "#userId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<VehicleRecommendation> getPersonalizedRecommendations(Long userId, Pageable pageable) {
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("Generating personalized recommendations for user: {}", userId);
            
            // Get user profile and behavior data
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            UserProfile userProfile = buildUserProfile(user);
            
            // Generate recommendations using hybrid approach
            List<VehicleRecommendation> recommendations = generateHybridRecommendations(userProfile, pageable.getPageSize() * 3);
            
            // Apply ML scoring and ranking
            recommendations = applyMLScoring(recommendations, userProfile);
            
            // Apply diversity filter to avoid similar recommendations
            recommendations = applyDiversityFilter(recommendations);
            
            // Paginate results
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), recommendations.size());
            List<VehicleRecommendation> pageContent = recommendations.subList(start, end);
            
            Page<VehicleRecommendation> result = new PageImpl<>(pageContent, pageable, recommendations.size());
            
            // Track performance metrics
            long duration = System.currentTimeMillis() - startTime;
            performanceService.recordApiCall("/ml/recommendations", duration, true);
            
            // Async: Track recommendation generation for future learning
            trackRecommendationGeneration(userId, recommendations);
            
            log.info("Generated {} recommendations for user {} in {}ms", 
                    pageContent.size(), userId, duration);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error generating recommendations for user {}: {}", userId, e.getMessage(), e);
            performanceService.recordApiCall("/ml/recommendations", 
                    System.currentTimeMillis() - startTime, false);
            throw new RuntimeException("Failed to generate recommendations", e);
        }
    }

    /**
     * Generate similar vehicle recommendations based on a specific vehicle
     */
    @Cacheable(value = "similarVehicles", key = "#vehicleId + '_' + #pageable.pageSize")
    public Page<VehicleRecommendation> getSimilarVehicles(Long vehicleId, Pageable pageable) {
        try {
            Vehicle targetVehicle = vehicleRepository.findById(vehicleId)
                    .orElseThrow(() -> new RuntimeException("Vehicle not found"));
            
            log.info("Finding vehicles similar to: {} {}", targetVehicle.getMake(), targetVehicle.getModel());
            
            // Content-based similarity using vehicle attributes
            List<VehicleRecommendation> similarities = findSimilarByAttributes(targetVehicle, pageable.getPageSize() * 2);
            
            // Apply advanced similarity scoring
            similarities = applySimilarityScoring(similarities, targetVehicle);
            
            // Paginate results
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), similarities.size());
            List<VehicleRecommendation> pageContent = similarities.subList(start, end);
            
            return new PageImpl<>(pageContent, pageable, similarities.size());
            
        } catch (Exception e) {
            log.error("Error finding similar vehicles for {}: {}", vehicleId, e.getMessage(), e);
            throw new RuntimeException("Failed to find similar vehicles", e);
        }
    }

    /**
     * Get trending vehicles with ML-based popularity scoring
     */
    @Cacheable(value = "trendingVehicles", key = "#pageable.pageSize")
    public Page<VehicleRecommendation> getTrendingVehicles(Pageable pageable) {
        try {
            log.info("Generating trending vehicle recommendations");
            
            // Get vehicles with recent activity
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
            List<Vehicle> recentVehicles = vehicleRepository.findByCreatedAtAfterOrUpdatedAtAfter(cutoff, cutoff);
            
            // Calculate trend scores
            List<VehicleRecommendation> trending = recentVehicles.stream()
                    .map(this::calculateTrendScore)
                    .sorted((r1, r2) -> Double.compare(r2.getScore(), r1.getScore()))
                    .limit(pageable.getPageSize())
                    .collect(Collectors.toList());
            
            return new PageImpl<>(trending, pageable, trending.size());
            
        } catch (Exception e) {
            log.error("Error generating trending vehicles: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get trending vehicles", e);
        }
    }

    /**
     * Generate market-based recommendations (hot deals, price drops, etc.)
     */
    @Cacheable(value = "marketRecommendations", key = "#userId + '_market'")
    public List<VehicleRecommendation> getMarketBasedRecommendations(Long userId) {
        try {
            log.info("Generating market-based recommendations for user: {}", userId);
            
            List<VehicleRecommendation> marketRecommendations = new ArrayList<>();
            
            // Find vehicles with significant price drops
            marketRecommendations.addAll(findPriceDropDeals());
            
            // Find undervalued vehicles based on market analysis
            marketRecommendations.addAll(findUndervaluedVehicles());
            
            // Find vehicles with high demand but low supply
            marketRecommendations.addAll(findHighDemandLowSupplyVehicles());
            
            // Sort by market attractiveness score
            return marketRecommendations.stream()
                    .sorted((r1, r2) -> Double.compare(r2.getScore(), r1.getScore()))
                    .limit(10)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error generating market recommendations: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // Private helper methods

    private UserProfile buildUserProfile(User user) {
        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        profile.setPreferences(userBehaviorService.getUserPreferences(user.getId()));
        profile.setSearchHistory(userBehaviorService.getSearchHistory(user.getId()));
        profile.setViewHistory(userBehaviorService.getViewHistory(user.getId()));
        profile.setBudgetRange(inferBudgetRange(user.getId()));
        profile.setLocationPreference(user.getLocation());
        return profile;
    }

    private List<VehicleRecommendation> generateHybridRecommendations(UserProfile profile, int count) {
        List<VehicleRecommendation> recommendations = new ArrayList<>();
        
        // Collaborative Filtering (40% weight)
        recommendations.addAll(generateCollaborativeFiltering(profile, (int)(count * 0.4)));
        
        // Content-Based Filtering (40% weight)
        recommendations.addAll(generateContentBasedFiltering(profile, (int)(count * 0.4)));
        
        // Popularity-Based (20% weight)
        recommendations.addAll(generatePopularityBasedFiltering(profile, (int)(count * 0.2)));
        
        return recommendations.stream()
                .collect(Collectors.toMap(
                    r -> r.getVehicle().getId(),
                    r -> r,
                    (r1, r2) -> r1.getScore() > r2.getScore() ? r1 : r2))
                .values()
                .stream()
                .collect(Collectors.toList());
    }

    private List<VehicleRecommendation> generateCollaborativeFiltering(UserProfile profile, int count) {
        // Find users with similar preferences
        List<Long> similarUsers = userBehaviorService.findSimilarUsers(profile.getUserId(), 50);
        
        // Get vehicles liked by similar users
        List<Vehicle> recommendedVehicles = new ArrayList<>();
        for (Long similarUserId : similarUsers) {
            recommendedVehicles.addAll(userBehaviorService.getFavoriteVehicles(similarUserId));
        }
        
        return recommendedVehicles.stream()
                .distinct()
                .limit(count)
                .map(vehicle -> new VehicleRecommendation(
                    vehicle,
                    calculateCollaborativeScore(vehicle, profile),
                    "Users with similar preferences also liked this vehicle",
                    "collaborative_filtering"
                ))
                .collect(Collectors.toList());
    }

    private List<VehicleRecommendation> generateContentBasedFiltering(UserProfile profile, int count) {
        // Analyze user's preferred vehicle attributes
        VehiclePreferences prefs = profile.getPreferences();
        
        List<Vehicle> vehicles = vehicleRepository.findByMakeInAndPriceBetweenAndYearGreaterThanEqual(
            prefs.getPreferredMakes(),
            prefs.getMinPrice(),
            prefs.getMaxPrice(),
            prefs.getMinYear()
        );
        
        return vehicles.stream()
                .limit(count)
                .map(vehicle -> new VehicleRecommendation(
                    vehicle,
                    calculateContentBasedScore(vehicle, profile),
                    "Based on your preferences for " + vehicle.getMake(),
                    "content_based"
                ))
                .collect(Collectors.toList());
    }

    private List<VehicleRecommendation> generatePopularityBasedFiltering(UserProfile profile, int count) {
        // Get most viewed and favorited vehicles
        List<Vehicle> popularVehicles = vehicleRepository.findMostPopularVehicles(count);
        
        return popularVehicles.stream()
                .map(vehicle -> new VehicleRecommendation(
                    vehicle,
                    calculatePopularityScore(vehicle),
                    "Popular choice among buyers",
                    "popularity_based"
                ))
                .collect(Collectors.toList());
    }

    private List<VehicleRecommendation> applyMLScoring(List<VehicleRecommendation> recommendations, UserProfile profile) {
        return recommendations.stream()
                .peek(rec -> {
                    double mlScore = calculateAdvancedMLScore(rec.getVehicle(), profile);
                    rec.setScore(rec.getScore() * 0.7 + mlScore * 0.3); // Blend original and ML scores
                })
                .sorted((r1, r2) -> Double.compare(r2.getScore(), r1.getScore()))
                .collect(Collectors.toList());
    }

    private double calculateAdvancedMLScore(Vehicle vehicle, UserProfile profile) {
        double score = 0.0;
        
        // Price alignment score
        if (isWithinBudget(vehicle.getPrice(), profile.getBudgetRange())) {
            score += 25.0;
        }
        
        // Brand preference score
        if (profile.getPreferences().getPreferredMakes().contains(vehicle.getMake())) {
            score += 20.0;
        }
        
        // Age preference score
        int vehicleAge = LocalDateTime.now().getYear() - vehicle.getYear();
        if (vehicleAge <= profile.getPreferences().getMaxAge()) {
            score += 15.0;
        }
        
        // Mileage preference score
        if (vehicle.getMileage() <= profile.getPreferences().getMaxMileage()) {
            score += 15.0;
        }
        
        // Market value alignment (using AI price prediction)
        double predictedPrice = pricePredictionService.predictPrice(vehicle);
        double valueFactor = predictedPrice / vehicle.getPrice().doubleValue();
        if (valueFactor >= 0.95 && valueFactor <= 1.05) { // Fair value
            score += 10.0;
        } else if (valueFactor > 1.05) { // Good deal
            score += 15.0;
        }
        
        // Location preference score
        if (vehicle.getLocation() != null && 
            vehicle.getLocation().equals(profile.getLocationPreference())) {
            score += 10.0;
        }
        
        return Math.min(score, 100.0); // Cap at 100
    }

    private List<VehicleRecommendation> applyDiversityFilter(List<VehicleRecommendation> recommendations) {
        // Ensure diversity in makes, models, and price ranges
        Map<String, Long> makeCount = new HashMap<>();
        List<VehicleRecommendation> diverseRecs = new ArrayList<>();
        
        for (VehicleRecommendation rec : recommendations) {
            String make = rec.getVehicle().getMake();
            long count = makeCount.getOrDefault(make, 0L);
            
            if (count < 3) { // Max 3 vehicles per make
                diverseRecs.add(rec);
                makeCount.put(make, count + 1);
            }
        }
        
        return diverseRecs;
    }

    private List<VehicleRecommendation> findSimilarByAttributes(Vehicle target, int count) {
        List<Vehicle> similar = vehicleRepository.findSimilarVehicles(
            target.getMake(),
            target.getModel(),
            target.getPrice().multiply(BigDecimal.valueOf(0.8)),
            target.getPrice().multiply(BigDecimal.valueOf(1.2)),
            target.getYear() - 2,
            target.getYear() + 2,
            count
        );
        
        return similar.stream()
                .filter(v -> !v.getId().equals(target.getId()))
                .map(vehicle -> new VehicleRecommendation(
                    vehicle,
                    0.0, // Score will be calculated separately
                    "Similar to " + target.getMake() + " " + target.getModel(),
                    "attribute_similarity"
                ))
                .collect(Collectors.toList());
    }

    private List<VehicleRecommendation> applySimilarityScoring(List<VehicleRecommendation> similarities, Vehicle target) {
        return similarities.stream()
                .peek(rec -> {
                    double similarityScore = calculateAttributeSimilarity(rec.getVehicle(), target);
                    rec.setScore(similarityScore);
                })
                .sorted((r1, r2) -> Double.compare(r2.getScore(), r1.getScore()))
                .collect(Collectors.toList());
    }

    private double calculateAttributeSimilarity(Vehicle vehicle, Vehicle target) {
        double similarity = 0.0;
        
        // Make and model exact match
        if (vehicle.getMake().equals(target.getMake())) similarity += 30.0;
        if (vehicle.getModel().equals(target.getModel())) similarity += 25.0;
        
        // Year similarity (closer years get higher scores)
        int yearDiff = Math.abs(vehicle.getYear() - target.getYear());
        similarity += Math.max(0, 20.0 - (yearDiff * 3.0));
        
        // Price similarity
        double priceDiff = Math.abs(vehicle.getPrice().doubleValue() - target.getPrice().doubleValue());
        double priceRatio = priceDiff / target.getPrice().doubleValue();
        similarity += Math.max(0, 15.0 - (priceRatio * 50.0));
        
        // Fuel type and transmission match
        if (vehicle.getFuelType().equals(target.getFuelType())) similarity += 5.0;
        if (vehicle.getTransmission().equals(target.getTransmission())) similarity += 5.0;
        
        return Math.min(similarity, 100.0);
    }

    private VehicleRecommendation calculateTrendScore(Vehicle vehicle) {
        double trendScore = 0.0;
        
        // Recency boost
        long daysSinceCreated = java.time.temporal.ChronoUnit.DAYS.between(
            vehicle.getCreatedAt(), LocalDateTime.now());
        trendScore += Math.max(0, 50.0 - daysSinceCreated * 2.0);
        
        // View count factor (would need view tracking)
        // trendScore += Math.min(vehicle.getViewCount() * 0.1, 25.0);
        
        // Engagement factor (likes, inquiries, etc.)
        // trendScore += calculateEngagementScore(vehicle);
        
        // Price competitiveness
        double marketPrice = pricePredictionService.predictPrice(vehicle);
        if (vehicle.getPrice().doubleValue() < marketPrice) {
            trendScore += 25.0; // Good deal bonus
        }
        
        return new VehicleRecommendation(
            vehicle,
            trendScore,
            "Trending vehicle with high market interest",
            "trending"
        );
    }

    private List<VehicleRecommendation> findPriceDropDeals() {
        // This would require price history tracking
        // For now, return vehicles that are potentially underpriced
        List<Vehicle> vehicles = vehicleRepository.findAll();
        
        return vehicles.stream()
                .filter(vehicle -> {
                    double marketPrice = pricePredictionService.predictPrice(vehicle);
                    return vehicle.getPrice().doubleValue() < marketPrice * 0.9; // 10% below market
                })
                .limit(5)
                .map(vehicle -> new VehicleRecommendation(
                    vehicle,
                    90.0,
                    "Great deal - priced below market value",
                    "price_drop"
                ))
                .collect(Collectors.toList());
    }

    private List<VehicleRecommendation> findUndervaluedVehicles() {
        // Find vehicles with good features but competitive pricing
        return vehicleRepository.findVehiclesWithHighFeaturesToPriceRatio()
                .stream()
                .limit(5)
                .map(vehicle -> new VehicleRecommendation(
                    vehicle,
                    85.0,
                    "Excellent value for features",
                    "undervalued"
                ))
                .collect(Collectors.toList());
    }

    private List<VehicleRecommendation> findHighDemandLowSupplyVehicles() {
        // This would require market demand analysis
        // For now, return newer vehicles with popular specs
        LocalDateTime recentDate = LocalDateTime.now().minusMonths(6);
        
        return vehicleRepository.findByCreatedAtAfterAndYearGreaterThan(recentDate, 2020)
                .stream()
                .limit(5)
                .map(vehicle -> new VehicleRecommendation(
                    vehicle,
                    80.0,
                    "High demand model in your area",
                    "high_demand"
                ))
                .collect(Collectors.toList());
    }

    // Utility methods
    
    private BudgetRange inferBudgetRange(Long userId) {
        List<Vehicle> viewedVehicles = userBehaviorService.getViewHistory(userId);
        if (viewedVehicles.isEmpty()) {
            return new BudgetRange(BigDecimal.ZERO, BigDecimal.valueOf(50000)); // Default range
        }
        
        DoubleSummaryStatistics priceStats = viewedVehicles.stream()
                .mapToDouble(v -> v.getPrice().doubleValue())
                .summaryStatistics();
        
        return new BudgetRange(
            BigDecimal.valueOf(priceStats.getMin() * 0.8),
            BigDecimal.valueOf(priceStats.getMax() * 1.2)
        );
    }

    private boolean isWithinBudget(BigDecimal price, BudgetRange budget) {
        return price.compareTo(budget.getMin()) >= 0 && price.compareTo(budget.getMax()) <= 0;
    }

    private double calculateCollaborativeScore(Vehicle vehicle, UserProfile profile) {
        // Simplified collaborative scoring
        return 75.0 + Math.random() * 25.0; // Random for now, would use actual ML model
    }

    private double calculateContentBasedScore(Vehicle vehicle, UserProfile profile) {
        double score = 0.0;
        
        VehiclePreferences prefs = profile.getPreferences();
        
        if (prefs.getPreferredMakes().contains(vehicle.getMake())) score += 30.0;
        if (isWithinBudget(vehicle.getPrice(), profile.getBudgetRange())) score += 25.0;
        if (vehicle.getYear() >= prefs.getMinYear()) score += 20.0;
        if (vehicle.getMileage() <= prefs.getMaxMileage()) score += 15.0;
        
        return score;
    }

    private double calculatePopularityScore(Vehicle vehicle) {
        // Would use actual view/like/inquiry counts
        return 60.0 + Math.random() * 40.0; // Random for now
    }

    @Async
    private void trackRecommendationGeneration(Long userId, List<VehicleRecommendation> recommendations) {
        try {
            // Log recommendation generation for ML model improvement
            userBehaviorService.trackRecommendationView(userId, 
                recommendations.stream()
                    .map(r -> r.getVehicle().getId())
                    .collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("Error tracking recommendation generation: {}", e.getMessage());
        }
    }

    // Inner classes for data structures

    public static class VehicleRecommendation {
        private Vehicle vehicle;
        private double score;
        private String reason;
        private String algorithm;
        private LocalDateTime generatedAt;

        public VehicleRecommendation(Vehicle vehicle, double score, String reason, String algorithm) {
            this.vehicle = vehicle;
            this.score = score;
            this.reason = reason;
            this.algorithm = algorithm;
            this.generatedAt = LocalDateTime.now();
        }

        // Getters and setters
        public Vehicle getVehicle() { return vehicle; }
        public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
        
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
        
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
    }

    public static class UserProfile {
        private Long userId;
        private VehiclePreferences preferences;
        private List<String> searchHistory;
        private List<Vehicle> viewHistory;
        private BudgetRange budgetRange;
        private String locationPreference;

        // Getters and setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        
        public VehiclePreferences getPreferences() { return preferences; }
        public void setPreferences(VehiclePreferences preferences) { this.preferences = preferences; }
        
        public List<String> getSearchHistory() { return searchHistory; }
        public void setSearchHistory(List<String> searchHistory) { this.searchHistory = searchHistory; }
        
        public List<Vehicle> getViewHistory() { return viewHistory; }
        public void setViewHistory(List<Vehicle> viewHistory) { this.viewHistory = viewHistory; }
        
        public BudgetRange getBudgetRange() { return budgetRange; }
        public void setBudgetRange(BudgetRange budgetRange) { this.budgetRange = budgetRange; }
        
        public String getLocationPreference() { return locationPreference; }
        public void setLocationPreference(String locationPreference) { this.locationPreference = locationPreference; }
    }

    public static class VehiclePreferences {
        private List<String> preferredMakes = new ArrayList<>();
        private List<String> preferredModels = new ArrayList<>();
        private BigDecimal minPrice = BigDecimal.ZERO;
        private BigDecimal maxPrice = BigDecimal.valueOf(100000);
        private int minYear = 2000;
        private int maxAge = 10;
        private long maxMileage = 150000;

        // Getters and setters
        public List<String> getPreferredMakes() { return preferredMakes; }
        public void setPreferredMakes(List<String> preferredMakes) { this.preferredMakes = preferredMakes; }
        
        public List<String> getPreferredModels() { return preferredModels; }
        public void setPreferredModels(List<String> preferredModels) { this.preferredModels = preferredModels; }
        
        public BigDecimal getMinPrice() { return minPrice; }
        public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
        
        public BigDecimal getMaxPrice() { return maxPrice; }
        public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }
        
        public int getMinYear() { return minYear; }
        public void setMinYear(int minYear) { this.minYear = minYear; }
        
        public int getMaxAge() { return maxAge; }
        public void setMaxAge(int maxAge) { this.maxAge = maxAge; }
        
        public long getMaxMileage() { return maxMileage; }
        public void setMaxMileage(long maxMileage) { this.maxMileage = maxMileage; }
    }

    public static class BudgetRange {
        private BigDecimal min;
        private BigDecimal max;

        public BudgetRange(BigDecimal min, BigDecimal max) {
            this.min = min;
            this.max = max;
        }

        public BigDecimal getMin() { return min; }
        public void setMin(BigDecimal min) { this.min = min; }
        
        public BigDecimal getMax() { return max; }
        public void setMax(BigDecimal max) { this.max = max; }
    }
}
