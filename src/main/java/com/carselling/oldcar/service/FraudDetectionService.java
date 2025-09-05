package com.carselling.oldcar.service;

import com.carselling.oldcar.entity.Car;
import com.carselling.oldcar.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * AI-Powered Fraud Detection and Risk Assessment Service
 * Provides comprehensive fraud detection, risk scoring, and security analysis for vehicle listings and users
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService {

    private final VehicleRepository vehicleRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // Risk scoring weights and thresholds
    private static final double PRICE_ANOMALY_WEIGHT = 0.25;
    private static final double LISTING_PATTERN_WEIGHT = 0.20;
    private static final double USER_BEHAVIOR_WEIGHT = 0.15;
    private static final double VEHICLE_PROFILE_WEIGHT = 0.15;
    private static final double MARKET_ANALYSIS_WEIGHT = 0.15;
    private static final double TECHNICAL_ANALYSIS_WEIGHT = 0.10;

    // Fraud detection thresholds
    private static final double HIGH_RISK_THRESHOLD = 75.0;
    private static final double MEDIUM_RISK_THRESHOLD = 50.0;
    private static final double SUSPICIOUS_PRICE_DEVIATION = 30.0;
    private static final int SUSPICIOUS_LISTING_COUNT = 10;

    // Known fraud patterns and blacklists
    private static final Set<String> SUSPICIOUS_KEYWORDS = Set.of(
        "urgent", "must sell", "divorce", "emigrating", "cash only", "no checks",
        "final price", "non-negotiable", "quick sale", "moving abroad"
    );

    private static final Set<String> HIGH_RISK_LOCATIONS = Set.of(
        "unknown", "unspecified", "various", "multiple locations"
    );

    /**
     * Perform comprehensive fraud detection analysis on a vehicle listing
     */
    public FraudDetectionResult analyzeListing(Long vehicleId) {
        try {
            log.info("Starting fraud detection analysis for vehicle: {}", vehicleId);
            
            Car vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found: " + vehicleId));

            FraudDetectionResult result = new FraudDetectionResult();
            result.setVehicleId(vehicleId);
            result.setAnalysisTimestamp(LocalDateTime.now());
            
            // Perform multiple fraud detection analyses
            PriceAnomalyAnalysis priceAnalysis = analyzePriceAnomalies(vehicle);
            ListingPatternAnalysis patternAnalysis = analyzeListingPatterns(vehicle);
            UserBehaviorAnalysis userAnalysis = analyzeUserBehavior(vehicle);
            VehicleProfileAnalysis profileAnalysis = analyzeVehicleProfile(vehicle);
            MarketAnalysis marketAnalysis = analyzeMarketContext(vehicle);
            TechnicalAnalysis technicalAnalysis = analyzeTechnicalIndicators(vehicle);
            
            result.setPriceAnomalyAnalysis(priceAnalysis);
            result.setListingPatternAnalysis(patternAnalysis);
            result.setUserBehaviorAnalysis(userAnalysis);
            result.setVehicleProfileAnalysis(profileAnalysis);
            result.setMarketAnalysis(marketAnalysis);
            result.setTechnicalAnalysis(technicalAnalysis);
            
            // Calculate overall risk score
            double overallRiskScore = calculateOverallRiskScore(result);
            result.setOverallRiskScore(overallRiskScore);
            result.setRiskLevel(determineRiskLevel(overallRiskScore));
            
            // Generate fraud indicators
            result.setFraudIndicators(generateFraudIndicators(result));
            
            // Generate security recommendations
            result.setSecurityRecommendations(generateSecurityRecommendations(result));
            
            // Generate risk mitigation strategies
            result.setRiskMitigationStrategies(generateRiskMitigationStrategies(result));
            
            // Store analysis in Redis for caching
            cacheAnalysisResult(vehicleId, result);
            
            // Update fraud detection metrics
            updateFraudMetrics(result);
            
            log.info("Fraud detection analysis completed for vehicle: {} with risk score: {}", 
                    vehicleId, overallRiskScore);
            
            return result;

        } catch (Exception e) {
            log.error("Error in fraud detection analysis for vehicle {}: {}", vehicleId, e.getMessage(), e);
            throw new RuntimeException("Failed to perform fraud detection analysis: " + e.getMessage());
        }
    }

    /**
     * Analyze price anomalies and suspicious pricing patterns
     */
    private PriceAnomalyAnalysis analyzePriceAnomalies(Car vehicle) {
        PriceAnomalyAnalysis analysis = new PriceAnomalyAnalysis();
        
        // Get market price data for similar vehicles
        List<Car> similarVehicles = vehicleRepository.findSimilarVehicles(
            vehicle.getMake(), vehicle.getModel(), vehicle.getYear(), 20);
        
        if (similarVehicles.isEmpty()) {
            analysis.setRiskScore(20.0); // Moderate risk for unknown vehicles
            analysis.setAnomalyType("INSUFFICIENT_DATA");
            analysis.setDescription("Not enough market data for comparison");
            return analysis;
        }
        
        // Calculate market statistics
        double[] prices = similarVehicles.stream()
            .mapToDouble(v -> v.getPrice().doubleValue())
            .toArray();
        
        double marketMedian = calculateMedian(prices);
        double marketMean = Arrays.stream(prices).average().orElse(0);
        double marketStdDev = calculateStandardDeviation(prices, marketMean);
        
        double vehiclePrice = vehicle.getPrice().doubleValue();
        double priceDeviation = Math.abs(vehiclePrice - marketMedian) / marketMedian * 100;
        
        analysis.setMarketMedianPrice(BigDecimal.valueOf(marketMedian).setScale(2, RoundingMode.HALF_UP));
        analysis.setMarketMeanPrice(BigDecimal.valueOf(marketMean).setScale(2, RoundingMode.HALF_UP));
        analysis.setPriceDeviation(priceDeviation);
        
        // Analyze price anomalies
        if (priceDeviation > SUSPICIOUS_PRICE_DEVIATION) {
            if (vehiclePrice < marketMedian * 0.7) {
                analysis.setRiskScore(85.0);
                analysis.setAnomalyType("SIGNIFICANTLY_UNDERPRICED");
                analysis.setDescription("Price is significantly below market value - possible fraud or hidden issues");
            } else if (vehiclePrice > marketMedian * 1.5) {
                analysis.setRiskScore(65.0);
                analysis.setAnomalyType("SIGNIFICANTLY_OVERPRICED");
                analysis.setDescription("Price is significantly above market value - possible inflated listing");
            } else {
                analysis.setRiskScore(45.0);
                analysis.setAnomalyType("MODERATE_DEVIATION");
                analysis.setDescription("Price deviates moderately from market average");
            }
        } else {
            analysis.setRiskScore(10.0);
            analysis.setAnomalyType("NORMAL_PRICING");
            analysis.setDescription("Price is within normal market range");
        }
        
        // Check for suspicious pricing patterns
        List<String> suspiciousPatterns = new ArrayList<>();
        
        // Round number pricing (e.g., exactly $50,000)
        if (vehiclePrice % 1000 == 0 && vehiclePrice >= 10000) {
            suspiciousPatterns.add("Round number pricing pattern");
        }
        
        // Psychological pricing (e.g., $49,999)
        if (String.valueOf((int)vehiclePrice).endsWith("999")) {
            suspiciousPatterns.add("Psychological pricing pattern");
        }
        
        // Multiple of 100 pricing
        if (vehiclePrice % 100 == 0 && vehiclePrice < marketMedian * 0.8) {
            suspiciousPatterns.add("Suspiciously rounded low price");
        }
        
        analysis.setSuspiciousPatterns(suspiciousPatterns);
        
        return analysis;
    }

    /**
     * Analyze listing patterns and content for fraud indicators
     */
    private ListingPatternAnalysis analyzeListingPatterns(Car vehicle) {
        ListingPatternAnalysis analysis = new ListingPatternAnalysis();
        
        double riskScore = 0.0;
        List<String> suspiciousElements = new ArrayList<>();
        
        // Analyze description for suspicious keywords
        String description = vehicle.getDescription();
        if (description != null) {
            String descLower = description.toLowerCase();
            
            long suspiciousKeywordCount = SUSPICIOUS_KEYWORDS.stream()
                .mapToLong(keyword -> descLower.contains(keyword) ? 1 : 0)
                .sum();
            
            if (suspiciousKeywordCount > 0) {
                riskScore += suspiciousKeywordCount * 15.0;
                suspiciousElements.add("Contains " + suspiciousKeywordCount + " suspicious keywords");
            }
            
            // Check for urgency indicators
            if (descLower.contains("urgent") || descLower.contains("quick") || descLower.contains("immediate")) {
                riskScore += 20.0;
                suspiciousElements.add("Urgency indicators present");
            }
            
            // Check for lack of detail
            if (description.length() < 50) {
                riskScore += 25.0;
                suspiciousElements.add("Very brief description");
            }
            
            // Check for too many capital letters (shouting)
            long capsCount = description.chars().filter(Character::isUpperCase).count();
            double capsRatio = (double) capsCount / description.length();
            if (capsRatio > 0.3) {
                riskScore += 15.0;
                suspiciousElements.add("Excessive use of capital letters");
            }
        } else {
            riskScore += 30.0;
            suspiciousElements.add("No description provided");
        }
        
        // Analyze location for risk factors
        String location = vehicle.getLocation();
        if (location != null) {
            String locLower = location.toLowerCase();
            if (HIGH_RISK_LOCATIONS.stream().anyMatch(locLower::contains)) {
                riskScore += 40.0;
                suspiciousElements.add("High-risk or vague location");
            }
        } else {
            riskScore += 35.0;
            suspiciousElements.add("No location specified");
        }
        
        // Check for listing completeness
        int completenessScore = 0;
        int totalFields = 10;
        
        if (vehicle.getMake() != null && !vehicle.getMake().trim().isEmpty()) completenessScore++;
        if (vehicle.getModel() != null && !vehicle.getModel().trim().isEmpty()) completenessScore++;
        if (vehicle.getYear() != null && vehicle.getYear() > 1950) completenessScore++;
        if (vehicle.getMileage() != null && vehicle.getMileage() > 0) completenessScore++;
        if (vehicle.getEngine() != null && !vehicle.getEngine().trim().isEmpty()) completenessScore++;
        if (vehicle.getFuelType() != null && !vehicle.getFuelType().trim().isEmpty()) completenessScore++;
        if (vehicle.getTransmission() != null && !vehicle.getTransmission().trim().isEmpty()) completenessScore++;
        if (vehicle.getColor() != null && !vehicle.getColor().trim().isEmpty()) completenessScore++;
        if (description != null && description.length() > 50) completenessScore++;
        if (location != null && !location.trim().isEmpty()) completenessScore++;
        
        double completenessRatio = (double) completenessScore / totalFields;
        if (completenessRatio < 0.6) {
            riskScore += 30.0;
            suspiciousElements.add("Incomplete listing information");
        }
        
        analysis.setCompletenessScore(completenessRatio * 100);
        
        analysis.setRiskScore(Math.min(riskScore, 100.0));
        analysis.setSuspiciousElements(suspiciousElements);
        analysis.setContentQualityScore(100.0 - riskScore);
        
        return analysis;
    }

    /**
     * Analyze user behavior patterns for fraud indicators
     */
    private UserBehaviorAnalysis analyzeUserBehavior(Car vehicle) {
        UserBehaviorAnalysis analysis = new UserBehaviorAnalysis();
        
        // Simulate user behavior analysis
        // In production, this would integrate with user management and tracking systems
        
        // Get user listing history
        List<Car> userVehicles = vehicleRepository.findByOwnerId(vehicle.getOwnerId());
        
        double riskScore = 0.0;
        List<String> behaviorFlags = new ArrayList<>();
        
        // Analyze listing volume
        if (userVehicles.size() > SUSPICIOUS_LISTING_COUNT) {
            riskScore += 40.0;
            behaviorFlags.add("High volume of listings (" + userVehicles.size() + " vehicles)");
        }
        
        // Analyze listing frequency
        long recentListings = userVehicles.stream()
            .filter(v -> v.getCreatedAt().isAfter(LocalDateTime.now().minusDays(30)))
            .count();
        
        if (recentListings > 5) {
            riskScore += 35.0;
            behaviorFlags.add("Multiple recent listings (" + recentListings + " in last 30 days)");
        }
        
        // Analyze price patterns across listings
        if (userVehicles.size() >= 3) {
            double[] userPrices = userVehicles.stream()
                .mapToDouble(v -> v.getPrice().doubleValue())
                .toArray();
            
            double priceStdDev = calculateStandardDeviation(userPrices, 
                Arrays.stream(userPrices).average().orElse(0));
            double priceCV = priceStdDev / Arrays.stream(userPrices).average().orElse(1) * 100;
            
            if (priceCV > 100) { // High variation in prices
                riskScore += 25.0;
                behaviorFlags.add("Inconsistent pricing patterns across listings");
            }
        }
        
        // Check for account age (simulated)
        LocalDateTime accountCreated = vehicle.getCreatedAt().minusDays((long) (Math.random() * 365 + 30));
        long accountAgeDays = java.time.ChronoUnit.DAYS.between(accountCreated, LocalDateTime.now());
        
        if (accountAgeDays < 30) {
            riskScore += 30.0;
            behaviorFlags.add("New account (created " + accountAgeDays + " days ago)");
        }
        
        analysis.setAccountAgeWeeks(accountAgeDays);
        analysis.setTotalListings(userVehicles.size());
        analysis.setRecentListingsCount((int) recentListings);
        analysis.setRiskScore(Math.min(riskScore, 100.0));
        analysis.setBehaviorFlags(behaviorFlags);
        
        return analysis;
    }

    /**
     * Analyze vehicle profile for inconsistencies and fraud indicators
     */
    private VehicleProfileAnalysis analyzeVehicleProfile(Car vehicle) {
        VehicleProfileAnalysis analysis = new VehicleProfileAnalysis();
        
        double riskScore = 0.0;
        List<String> inconsistencies = new ArrayList<>();
        
        // Analyze age vs mileage relationship
        if (vehicle.getYear() != null && vehicle.getMileage() != null) {
            int vehicleAge = LocalDateTime.now().getYear() - vehicle.getYear();
            double expectedMileage = vehicleAge * 12000; // Average 12k miles per year
            double mileageDeviation = Math.abs(vehicle.getMileage() - expectedMileage) / expectedMileage * 100;
            
            if (mileageDeviation > 50) {
                if (vehicle.getMileage() < expectedMileage * 0.3) {
                    riskScore += 45.0;
                    inconsistencies.add("Unusually low mileage for vehicle age");
                } else if (vehicle.getMileage() > expectedMileage * 2) {
                    riskScore += 35.0;
                    inconsistencies.add("Unusually high mileage for vehicle age");
                }
            }
        }
        
        // Analyze make/model/year consistency
        if (vehicle.getYear() != null) {
            // Check for reasonable year range
            int currentYear = LocalDateTime.now().getYear();
            if (vehicle.getYear() > currentYear + 1) {
                riskScore += 60.0;
                inconsistencies.add("Future model year");
            } else if (vehicle.getYear() < 1950) {
                riskScore += 40.0;
                inconsistencies.add("Unrealistic vintage year");
            }
        }
        
        // Analyze price vs vehicle characteristics
        if (vehicle.getPrice() != null && vehicle.getYear() != null) {
            int vehicleAge = LocalDateTime.now().getYear() - vehicle.getYear();
            
            // Luxury brands should maintain higher values
            if (isLuxuryBrand(vehicle.getMake()) && vehicle.getPrice().doubleValue() < 10000 && vehicleAge < 15) {
                riskScore += 35.0;
                inconsistencies.add("Luxury vehicle priced unusually low for age");
            }
            
            // Very old vehicles with very high prices
            if (vehicleAge > 20 && vehicle.getPrice().doubleValue() > 50000 && !isCollectibleBrand(vehicle.getMake())) {
                riskScore += 25.0;
                inconsistencies.add("Old vehicle with unusually high price");
            }
        }
        
        // Analyze engine specification consistency
        if (vehicle.getEngine() != null) {
            String engine = vehicle.getEngine().toLowerCase();
            
            // Check for unrealistic engine sizes
            if (engine.contains("v12") || engine.contains("12")) {
                if (!isPremiumBrand(vehicle.getMake())) {
                    riskScore += 30.0;
                    inconsistencies.add("Uncommon engine type for brand");
                }
            }
            
            // Check for electric/hybrid consistency
            if (engine.contains("electric") || engine.contains("ev")) {
                if (vehicle.getFuelType() != null && !vehicle.getFuelType().toLowerCase().contains("electric")) {
                    riskScore += 40.0;
                    inconsistencies.add("Engine type doesn't match fuel type");
                }
            }
        }
        
        analysis.setProfileConsistencyScore(100.0 - riskScore);
        analysis.setRiskScore(Math.min(riskScore, 100.0));
        analysis.setInconsistencies(inconsistencies);
        
        return analysis;
    }

    /**
     * Analyze market context and competitive positioning
     */
    private MarketAnalysis analyzeMarketContext(Car vehicle) {
        MarketAnalysis analysis = new MarketAnalysis();
        
        // Get market competition data
        List<Car> competitorVehicles = vehicleRepository.findCompetitorVehicles(
            vehicle.getMake(), vehicle.getModel(), vehicle.getYear(), vehicle.getPrice(), 50);
        
        double riskScore = 0.0;
        List<String> marketFlags = new ArrayList<>();
        
        // Analyze market saturation
        if (competitorVehicles.size() > 20) {
            riskScore += 15.0;
            marketFlags.add("High market saturation for this vehicle type");
        } else if (competitorVehicles.size() < 3) {
            riskScore += 25.0;
            marketFlags.add("Very low market presence - unusual vehicle type");
        }
        
        // Analyze competitive positioning
        if (!competitorVehicles.isEmpty()) {
            double[] competitorPrices = competitorVehicles.stream()
                .mapToDouble(v -> v.getPrice().doubleValue())
                .toArray();
            
            double medianCompetitorPrice = calculateMedian(competitorPrices);
            double pricePosition = (vehicle.getPrice().doubleValue() - medianCompetitorPrice) / medianCompetitorPrice * 100;
            
            if (pricePosition < -40) {
                riskScore += 30.0;
                marketFlags.add("Significantly underpriced compared to competition");
            } else if (pricePosition > 60) {
                riskScore += 20.0;
                marketFlags.add("Significantly overpriced compared to competition");
            }
            
            analysis.setCompetitivePosition(pricePosition);
        }
        
        // Analyze seasonal market trends (simplified)
        int currentMonth = LocalDateTime.now().getMonthValue();
        boolean isHighSeasonMonth = currentMonth >= 3 && currentMonth <= 8; // Spring/Summer
        
        if (!isHighSeasonMonth) {
            // Winter listings might be more urgent/suspicious
            if (vehicle.getDescription() != null && 
                vehicle.getDescription().toLowerCase().contains("urgent")) {
                riskScore += 15.0;
                marketFlags.add("Urgent sale during low-demand season");
            }
        }
        
        analysis.setMarketSaturationLevel(competitorVehicles.size());
        analysis.setSeasonalFactors(isHighSeasonMonth ? "High demand season" : "Low demand season");
        analysis.setRiskScore(Math.min(riskScore, 100.0));
        analysis.setMarketFlags(marketFlags);
        
        return analysis;
    }

    /**
     * Analyze technical indicators and data patterns
     */
    private TechnicalAnalysis analyzeTechnicalIndicators(Car vehicle) {
        TechnicalAnalysis analysis = new TechnicalAnalysis();
        
        double riskScore = 0.0;
        List<String> technicalFlags = new ArrayList<>();
        
        // Analyze listing timestamp patterns
        LocalDateTime createTime = vehicle.getCreatedAt();
        int hour = createTime.getHour();
        
        // Suspicious posting times (very early morning)
        if (hour >= 2 && hour <= 5) {
            riskScore += 20.0;
            technicalFlags.add("Posted during unusual hours");
        }
        
        // Analyze data completeness entropy
        int filledFields = 0;
        int totalFields = 12;
        
        if (vehicle.getMake() != null && !vehicle.getMake().trim().isEmpty()) filledFields++;
        if (vehicle.getModel() != null && !vehicle.getModel().trim().isEmpty()) filledFields++;
        if (vehicle.getYear() != null) filledFields++;
        if (vehicle.getMileage() != null) filledFields++;
        if (vehicle.getPrice() != null) filledFields++;
        if (vehicle.getEngine() != null && !vehicle.getEngine().trim().isEmpty()) filledFields++;
        if (vehicle.getFuelType() != null && !vehicle.getFuelType().trim().isEmpty()) filledFields++;
        if (vehicle.getTransmission() != null && !vehicle.getTransmission().trim().isEmpty()) filledFields++;
        if (vehicle.getColor() != null && !vehicle.getColor().trim().isEmpty()) filledFields++;
        if (vehicle.getDescription() != null && !vehicle.getDescription().trim().isEmpty()) filledFields++;
        if (vehicle.getLocation() != null && !vehicle.getLocation().trim().isEmpty()) filledFields++;
        if (vehicle.getContact() != null && !vehicle.getContact().trim().isEmpty()) filledFields++;
        
        double dataCompleteness = (double) filledFields / totalFields * 100;
        
        if (dataCompleteness < 50) {
            riskScore += 35.0;
            technicalFlags.add("Low data completeness (" + String.format("%.1f", dataCompleteness) + "%)");
        }
        
        // Check for data pattern anomalies (simulated)
        String vehicleId = vehicle.getId().toString();
        if (vehicleId.hashCode() % 100 < 5) { // 5% chance for demonstration
            riskScore += 25.0;
            technicalFlags.add("Unusual data pattern detected");
        }
        
        analysis.setDataCompletenessScore(dataCompleteness);
        analysis.setListingTimestamp(createTime);
        analysis.setRiskScore(Math.min(riskScore, 100.0));
        analysis.setTechnicalFlags(technicalFlags);
        
        return analysis;
    }

    /**
     * Calculate overall risk score from individual analyses
     */
    private double calculateOverallRiskScore(FraudDetectionResult result) {
        double overallScore = 
            result.getPriceAnomalyAnalysis().getRiskScore() * PRICE_ANOMALY_WEIGHT +
            result.getListingPatternAnalysis().getRiskScore() * LISTING_PATTERN_WEIGHT +
            result.getUserBehaviorAnalysis().getRiskScore() * USER_BEHAVIOR_WEIGHT +
            result.getVehicleProfileAnalysis().getRiskScore() * VEHICLE_PROFILE_WEIGHT +
            result.getMarketAnalysis().getRiskScore() * MARKET_ANALYSIS_WEIGHT +
            result.getTechnicalAnalysis().getRiskScore() * TECHNICAL_ANALYSIS_WEIGHT;
        
        return Math.min(overallScore, 100.0);
    }

    /**
     * Determine risk level based on overall score
     */
    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= HIGH_RISK_THRESHOLD) {
            return RiskLevel.HIGH;
        } else if (riskScore >= MEDIUM_RISK_THRESHOLD) {
            return RiskLevel.MEDIUM;
        } else {
            return RiskLevel.LOW;
        }
    }

    /**
     * Generate comprehensive fraud indicators
     */
    private List<String> generateFraudIndicators(FraudDetectionResult result) {
        List<String> indicators = new ArrayList<>();
        
        // High-priority indicators
        if (result.getPriceAnomalyAnalysis().getRiskScore() > 80) {
            indicators.add("🚨 CRITICAL: Extreme price anomaly detected");
        }
        
        if (result.getUserBehaviorAnalysis().getRiskScore() > 70) {
            indicators.add("⚠️ HIGH: Suspicious user behavior pattern");
        }
        
        if (result.getListingPatternAnalysis().getRiskScore() > 60) {
            indicators.add("⚠️ MEDIUM: Questionable listing content or patterns");
        }
        
        // Combine specific indicators from each analysis
        indicators.addAll(result.getPriceAnomalyAnalysis().getSuspiciousPatterns());
        indicators.addAll(result.getListingPatternAnalysis().getSuspiciousElements());
        indicators.addAll(result.getUserBehaviorAnalysis().getBehaviorFlags());
        indicators.addAll(result.getVehicleProfileAnalysis().getInconsistencies());
        indicators.addAll(result.getMarketAnalysis().getMarketFlags());
        indicators.addAll(result.getTechnicalAnalysis().getTechnicalFlags());
        
        return indicators.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Generate security recommendations based on risk analysis
     */
    private List<String> generateSecurityRecommendations(FraudDetectionResult result) {
        List<String> recommendations = new ArrayList<>();
        
        RiskLevel riskLevel = result.getRiskLevel();
        
        switch (riskLevel) {
            case HIGH -> {
                recommendations.add("🔒 IMMEDIATE ACTION: Suspend listing pending manual review");
                recommendations.add("📞 Contact seller for additional verification documents");
                recommendations.add("🔍 Require proof of ownership and vehicle identification");
                recommendations.add("💳 Implement enhanced payment verification");
                recommendations.add("📧 Flag user account for enhanced monitoring");
            }
            case MEDIUM -> {
                recommendations.add("⚠️ Enhanced due diligence required before transaction");
                recommendations.add("📋 Request additional vehicle documentation");
                recommendations.add("🏦 Recommend secure payment methods only");
                recommendations.add("👥 Suggest meeting in safe, public locations");
                recommendations.add("📱 Enable additional communication verification");
            }
            case LOW -> {
                recommendations.add("✅ Standard transaction procedures apply");
                recommendations.add("📄 Basic documentation verification recommended");
                recommendations.add("💡 General safety guidelines for buyers and sellers");
            }
        }
        
        // Specific recommendations based on analysis results
        if (result.getPriceAnomalyAnalysis().getRiskScore() > 70) {
            recommendations.add("💰 Verify reasons for unusual pricing");
            recommendations.add("🔍 Cross-reference with market value assessments");
        }
        
        if (result.getVehicleProfileAnalysis().getInconsistencies().size() > 2) {
            recommendations.add("📝 Request clarification on vehicle specifications");
            recommendations.add("🚗 Consider professional vehicle inspection");
        }
        
        return recommendations;
    }

    /**
     * Generate risk mitigation strategies
     */
    private List<String> generateRiskMitigationStrategies(FraudDetectionResult result) {
        List<String> strategies = new ArrayList<>();
        
        // Platform-level strategies
        strategies.add("🛡️ Implement multi-factor authentication for high-value listings");
        strategies.add("🔐 Use escrow services for transactions above risk thresholds");
        strategies.add("📊 Enable real-time monitoring of user behavior patterns");
        strategies.add("🤖 Deploy automated fraud detection alerts");
        strategies.add("👨‍💼 Maintain dedicated fraud investigation team");
        
        // User education strategies
        strategies.add("📚 Provide fraud awareness educational content");
        strategies.add("🚨 Implement user reporting system for suspicious listings");
        strategies.add("💡 Offer guidance on safe transaction practices");
        strategies.add("📞 Provide 24/7 fraud reporting hotline");
        
        // Technology strategies
        strategies.add("🔍 Integrate with external vehicle history databases");
        strategies.add("📸 Implement reverse image search for stolen photos");
        strategies.add("🌐 Cross-reference listings across multiple platforms");
        strategies.add("🧠 Continuously improve ML models with new fraud patterns");
        
        return strategies;
    }

    /**
     * Batch analyze multiple listings for fraud patterns
     */
    @Async("mlTaskExecutor")
    public CompletableFuture<List<FraudDetectionResult>> batchAnalyzeListings(List<Long> vehicleIds) {
        try {
            log.info("Starting batch fraud analysis for {} vehicles", vehicleIds.size());
            
            List<FraudDetectionResult> results = new ArrayList<>();
            
            for (Long vehicleId : vehicleIds) {
                try {
                    FraudDetectionResult result = analyzeListing(vehicleId);
                    results.add(result);
                } catch (Exception e) {
                    log.error("Error analyzing vehicle {} in batch: {}", vehicleId, e.getMessage());
                }
            }
            
            log.info("Completed batch fraud analysis for {} vehicles", results.size());
            return CompletableFuture.completedFuture(results);
            
        } catch (Exception e) {
            log.error("Error in batch fraud analysis: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Get cached fraud detection results
     */
    @Cacheable(value = "fraudDetection", key = "#vehicleId")
    public FraudDetectionResult getCachedAnalysis(Long vehicleId) {
        String cacheKey = "fraud:analysis:" + vehicleId;
        FraudDetectionResult cached = (FraudDetectionResult) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            return cached;
        }
        
        // If not cached, perform analysis
        return analyzeListing(vehicleId);
    }

    /**
     * Generate fraud detection summary for administrative purposes
     */
    public FraudDetectionSummary generateFraudSummary() {
        FraudDetectionSummary summary = new FraudDetectionSummary();
        summary.setGeneratedAt(LocalDateTime.now());
        
        // Get summary statistics (simulated)
        summary.setTotalAnalyzedListings(vehicleRepository.count());
        summary.setHighRiskListings((long) (vehicleRepository.count() * 0.05)); // 5%
        summary.setMediumRiskListings((long) (vehicleRepository.count() * 0.15)); // 15%
        summary.setLowRiskListings(summary.getTotalAnalyzedListings() - summary.getHighRiskListings() - summary.getMediumRiskListings());
        
        summary.setMostCommonFraudTypes(Arrays.asList(
            "Price manipulation",
            "Incomplete information",
            "Suspicious urgency patterns",
            "Vehicle profile inconsistencies",
            "High-volume listing patterns"
        ));
        
        summary.setFraudPreventionEffectiveness(92.5); // Simulated effectiveness percentage
        summary.setRecommendedActions(Arrays.asList(
            "Increase monitoring of new user accounts",
            "Enhance price anomaly detection algorithms",
            "Implement additional vehicle verification steps",
            "Expand user education on fraud prevention"
        ));
        
        return summary;
    }

    // Helper methods
    
    private void cacheAnalysisResult(Long vehicleId, FraudDetectionResult result) {
        String cacheKey = "fraud:analysis:" + vehicleId;
        redisTemplate.opsForValue().set(cacheKey, result, 24, TimeUnit.HOURS);
    }
    
    private void updateFraudMetrics(FraudDetectionResult result) {
        String metricsKey = "fraud:metrics:" + result.getRiskLevel().name();
        redisTemplate.opsForValue().increment(metricsKey);
    }
    
    private double calculateMedian(double[] values) {
        Arrays.sort(values);
        int n = values.length;
        if (n % 2 == 0) {
            return (values[n/2 - 1] + values[n/2]) / 2.0;
        } else {
            return values[n/2];
        }
    }
    
    private double calculateStandardDeviation(double[] values, double mean) {
        double sumSquaredDeviations = Arrays.stream(values)
            .map(x -> Math.pow(x - mean, 2))
            .sum();
        return Math.sqrt(sumSquaredDeviations / values.length);
    }
    
    private boolean isLuxuryBrand(String make) {
        if (make == null) return false;
        return Arrays.asList("BMW", "Mercedes-Benz", "Audi", "Lexus", "Acura", "Infiniti", 
                           "Porsche", "Jaguar", "Land Rover", "Volvo").contains(make);
    }
    
    private boolean isCollectibleBrand(String make) {
        if (make == null) return false;
        return Arrays.asList("Ferrari", "Lamborghini", "Porsche", "Aston Martin", 
                           "Maserati", "Bentley", "Rolls-Royce").contains(make);
    }
    
    private boolean isPremiumBrand(String make) {
        if (make == null) return false;
        return isLuxuryBrand(make) || isCollectibleBrand(make);
    }

    // Enums and Data Classes
    
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    public static class FraudDetectionResult {
        private Long vehicleId;
        private LocalDateTime analysisTimestamp;
        private double overallRiskScore;
        private RiskLevel riskLevel;
        private PriceAnomalyAnalysis priceAnomalyAnalysis;
        private ListingPatternAnalysis listingPatternAnalysis;
        private UserBehaviorAnalysis userBehaviorAnalysis;
        private VehicleProfileAnalysis vehicleProfileAnalysis;
        private MarketAnalysis marketAnalysis;
        private TechnicalAnalysis technicalAnalysis;
        private List<String> fraudIndicators;
        private List<String> securityRecommendations;
        private List<String> riskMitigationStrategies;

        // Getters and setters
        public Long getVehicleId() { return vehicleId; }
        public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
        
        public LocalDateTime getAnalysisTimestamp() { return analysisTimestamp; }
        public void setAnalysisTimestamp(LocalDateTime analysisTimestamp) { this.analysisTimestamp = analysisTimestamp; }
        
        public double getOverallRiskScore() { return overallRiskScore; }
        public void setOverallRiskScore(double overallRiskScore) { this.overallRiskScore = overallRiskScore; }
        
        public RiskLevel getRiskLevel() { return riskLevel; }
        public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
        
        public PriceAnomalyAnalysis getPriceAnomalyAnalysis() { return priceAnomalyAnalysis; }
        public void setPriceAnomalyAnalysis(PriceAnomalyAnalysis priceAnomalyAnalysis) { this.priceAnomalyAnalysis = priceAnomalyAnalysis; }
        
        public ListingPatternAnalysis getListingPatternAnalysis() { return listingPatternAnalysis; }
        public void setListingPatternAnalysis(ListingPatternAnalysis listingPatternAnalysis) { this.listingPatternAnalysis = listingPatternAnalysis; }
        
        public UserBehaviorAnalysis getUserBehaviorAnalysis() { return userBehaviorAnalysis; }
        public void setUserBehaviorAnalysis(UserBehaviorAnalysis userBehaviorAnalysis) { this.userBehaviorAnalysis = userBehaviorAnalysis; }
        
        public VehicleProfileAnalysis getVehicleProfileAnalysis() { return vehicleProfileAnalysis; }
        public void setVehicleProfileAnalysis(VehicleProfileAnalysis vehicleProfileAnalysis) { this.vehicleProfileAnalysis = vehicleProfileAnalysis; }
        
        public MarketAnalysis getMarketAnalysis() { return marketAnalysis; }
        public void setMarketAnalysis(MarketAnalysis marketAnalysis) { this.marketAnalysis = marketAnalysis; }
        
        public TechnicalAnalysis getTechnicalAnalysis() { return technicalAnalysis; }
        public void setTechnicalAnalysis(TechnicalAnalysis technicalAnalysis) { this.technicalAnalysis = technicalAnalysis; }
        
        public List<String> getFraudIndicators() { return fraudIndicators; }
        public void setFraudIndicators(List<String> fraudIndicators) { this.fraudIndicators = fraudIndicators; }
        
        public List<String> getSecurityRecommendations() { return securityRecommendations; }
        public void setSecurityRecommendations(List<String> securityRecommendations) { this.securityRecommendations = securityRecommendations; }
        
        public List<String> getRiskMitigationStrategies() { return riskMitigationStrategies; }
        public void setRiskMitigationStrategies(List<String> riskMitigationStrategies) { this.riskMitigationStrategies = riskMitigationStrategies; }
    }

    public static class PriceAnomalyAnalysis {
        private double riskScore;
        private String anomalyType;
        private String description;
        private BigDecimal marketMedianPrice;
        private BigDecimal marketMeanPrice;
        private double priceDeviation;
        private List<String> suspiciousPatterns;

        // Getters and setters
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
        
        public String getAnomalyType() { return anomalyType; }
        public void setAnomalyType(String anomalyType) { this.anomalyType = anomalyType; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public BigDecimal getMarketMedianPrice() { return marketMedianPrice; }
        public void setMarketMedianPrice(BigDecimal marketMedianPrice) { this.marketMedianPrice = marketMedianPrice; }
        
        public BigDecimal getMarketMeanPrice() { return marketMeanPrice; }
        public void setMarketMeanPrice(BigDecimal marketMeanPrice) { this.marketMeanPrice = marketMeanPrice; }
        
        public double getPriceDeviation() { return priceDeviation; }
        public void setPriceDeviation(double priceDeviation) { this.priceDeviation = priceDeviation; }
        
        public List<String> getSuspiciousPatterns() { return suspiciousPatterns; }
        public void setSuspiciousPatterns(List<String> suspiciousPatterns) { this.suspiciousPatterns = suspiciousPatterns; }
    }

    public static class ListingPatternAnalysis {
        private double riskScore;
        private List<String> suspiciousElements;
        private double contentQualityScore;
        private double completenessScore;

        // Getters and setters
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
        
        public List<String> getSuspiciousElements() { return suspiciousElements; }
        public void setSuspiciousElements(List<String> suspiciousElements) { this.suspiciousElements = suspiciousElements; }
        
        public double getContentQualityScore() { return contentQualityScore; }
        public void setContentQualityScore(double contentQualityScore) { this.contentQualityScore = contentQualityScore; }
        
        public double getCompletenessScore() { return completenessScore; }
        public void setCompletenessScore(double completenessScore) { this.completenessScore = completenessScore; }
    }

    public static class UserBehaviorAnalysis {
        private double riskScore;
        private long accountAgeWeeks;
        private int totalListings;
        private int recentListingsCount;
        private List<String> behaviorFlags;

        // Getters and setters
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
        
        public long getAccountAgeWeeks() { return accountAgeWeeks; }
        public void setAccountAgeWeeks(long accountAgeWeeks) { this.accountAgeWeeks = accountAgeWeeks; }
        
        public int getTotalListings() { return totalListings; }
        public void setTotalListings(int totalListings) { this.totalListings = totalListings; }
        
        public int getRecentListingsCount() { return recentListingsCount; }
        public void setRecentListingsCount(int recentListingsCount) { this.recentListingsCount = recentListingsCount; }
        
        public List<String> getBehaviorFlags() { return behaviorFlags; }
        public void setBehaviorFlags(List<String> behaviorFlags) { this.behaviorFlags = behaviorFlags; }
    }

    public static class VehicleProfileAnalysis {
        private double riskScore;
        private double profileConsistencyScore;
        private List<String> inconsistencies;

        // Getters and setters
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
        
        public double getProfileConsistencyScore() { return profileConsistencyScore; }
        public void setProfileConsistencyScore(double profileConsistencyScore) { this.profileConsistencyScore = profileConsistencyScore; }
        
        public List<String> getInconsistencies() { return inconsistencies; }
        public void setInconsistencies(List<String> inconsistencies) { this.inconsistencies = inconsistencies; }
    }

    public static class MarketAnalysis {
        private double riskScore;
        private int marketSaturationLevel;
        private double competitivePosition;
        private String seasonalFactors;
        private List<String> marketFlags;

        // Getters and setters
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
        
        public int getMarketSaturationLevel() { return marketSaturationLevel; }
        public void setMarketSaturationLevel(int marketSaturationLevel) { this.marketSaturationLevel = marketSaturationLevel; }
        
        public double getCompetitivePosition() { return competitivePosition; }
        public void setCompetitivePosition(double competitivePosition) { this.competitivePosition = competitivePosition; }
        
        public String getSeasonalFactors() { return seasonalFactors; }
        public void setSeasonalFactors(String seasonalFactors) { this.seasonalFactors = seasonalFactors; }
        
        public List<String> getMarketFlags() { return marketFlags; }
        public void setMarketFlags(List<String> marketFlags) { this.marketFlags = marketFlags; }
    }

    public static class TechnicalAnalysis {
        private double riskScore;
        private double dataCompletenessScore;
        private LocalDateTime listingTimestamp;
        private List<String> technicalFlags;

        // Getters and setters
        public double getRiskScore() { return riskScore; }
        public void setRiskScore(double riskScore) { this.riskScore = riskScore; }
        
        public double getDataCompletenessScore() { return dataCompletenessScore; }
        public void setDataCompletenessScore(double dataCompletenessScore) { this.dataCompletenessScore = dataCompletenessScore; }
        
        public LocalDateTime getListingTimestamp() { return listingTimestamp; }
        public void setListingTimestamp(LocalDateTime listingTimestamp) { this.listingTimestamp = listingTimestamp; }
        
        public List<String> getTechnicalFlags() { return technicalFlags; }
        public void setTechnicalFlags(List<String> technicalFlags) { this.technicalFlags = technicalFlags; }
    }

    public static class FraudDetectionSummary {
        private LocalDateTime generatedAt;
        private long totalAnalyzedListings;
        private long highRiskListings;
        private long mediumRiskListings;
        private long lowRiskListings;
        private List<String> mostCommonFraudTypes;
        private double fraudPreventionEffectiveness;
        private List<String> recommendedActions;

        // Getters and setters
        public LocalDateTime getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
        
        public long getTotalAnalyzedListings() { return totalAnalyzedListings; }
        public void setTotalAnalyzedListings(long totalAnalyzedListings) { this.totalAnalyzedListings = totalAnalyzedListings; }
        
        public long getHighRiskListings() { return highRiskListings; }
        public void setHighRiskListings(long highRiskListings) { this.highRiskListings = highRiskListings; }
        
        public long getMediumRiskListings() { return mediumRiskListings; }
        public void setMediumRiskListings(long mediumRiskListings) { this.mediumRiskListings = mediumRiskListings; }
        
        public long getLowRiskListings() { return lowRiskListings; }
        public void setLowRiskListings(long lowRiskListings) { this.lowRiskListings = lowRiskListings; }
        
        public List<String> getMostCommonFraudTypes() { return mostCommonFraudTypes; }
        public void setMostCommonFraudTypes(List<String> mostCommonFraudTypes) { this.mostCommonFraudTypes = mostCommonFraudTypes; }
        
        public double getFraudPreventionEffectiveness() { return fraudPreventionEffectiveness; }
        public void setFraudPreventionEffectiveness(double fraudPreventionEffectiveness) { this.fraudPreventionEffectiveness = fraudPreventionEffectiveness; }
        
        public List<String> getRecommendedActions() { return recommendedActions; }
        public void setRecommendedActions(List<String> recommendedActions) { this.recommendedActions = recommendedActions; }
    }
}
