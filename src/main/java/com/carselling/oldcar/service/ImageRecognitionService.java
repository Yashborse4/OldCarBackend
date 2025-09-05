package com.carselling.oldcar.service;

import com.carselling.oldcar.entity.Car;
import com.carselling.oldcar.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI-Powered Image Recognition and Classification Service
 * Provides computer vision capabilities for vehicle image analysis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageRecognitionService {

    private final VehicleRepository vehicleRepository;

    // Simulated AI models - In production, integrate with actual CV services like AWS Rekognition, Azure Computer Vision, or Google Vision API
    private static final Map<String, Double> VEHICLE_FEATURES_CONFIDENCE = Map.of(
        "exterior_condition", 0.85,
        "interior_condition", 0.78,
        "wheel_condition", 0.82,
        "paint_quality", 0.88,
        "damage_assessment", 0.91,
        "feature_detection", 0.79
    );

    // Vehicle feature categories for classification
    private static final List<String> EXTERIOR_FEATURES = Arrays.asList(
        "LED_HEADLIGHTS", "SUNROOF", "SPOILER", "ROOF_RAILS", "ALLOY_WHEELS",
        "FOG_LIGHTS", "TINTED_WINDOWS", "SIDE_STEPS", "BULL_BAR", "CHROME_TRIM"
    );

    private static final List<String> INTERIOR_FEATURES = Arrays.asList(
        "LEATHER_SEATS", "HEATED_SEATS", "NAVIGATION_SYSTEM", "BACKUP_CAMERA",
        "PREMIUM_SOUND_SYSTEM", "DUAL_ZONE_CLIMATE", "POWER_SEATS", "WOODEN_TRIM",
        "AMBIENT_LIGHTING", "PANORAMIC_ROOF"
    );

    private static final List<String> SAFETY_FEATURES = Arrays.asList(
        "ABS", "AIRBAGS", "STABILITY_CONTROL", "BLIND_SPOT_MONITORING",
        "LANE_DEPARTURE_WARNING", "COLLISION_AVOIDANCE", "PARKING_SENSORS",
        "ADAPTIVE_CRUISE_CONTROL", "EMERGENCY_BRAKING", "TIRE_PRESSURE_MONITORING"
    );

    /**
     * Analyze vehicle images and extract comprehensive information
     */
    public ImageAnalysisResult analyzeVehicleImages(List<MultipartFile> images, Long vehicleId) {
        try {
            log.info("Starting image analysis for vehicle: {}", vehicleId);
            
            Car vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found: " + vehicleId));

            ImageAnalysisResult result = new ImageAnalysisResult();
            result.setVehicleId(vehicleId);
            result.setAnalysisTimestamp(LocalDateTime.now());
            result.setTotalImages(images.size());
            
            List<ImageAnalysis> individualAnalyses = new ArrayList<>();
            
            // Analyze each image
            for (int i = 0; i < images.size(); i++) {
                MultipartFile image = images.get(i);
                ImageAnalysis analysis = analyzeSingleImage(image, i, vehicle);
                individualAnalyses.add(analysis);
            }
            
            result.setImageAnalyses(individualAnalyses);
            
            // Generate comprehensive vehicle assessment
            VehicleAssessment assessment = generateVehicleAssessment(individualAnalyses, vehicle);
            result.setVehicleAssessment(assessment);
            
            // Calculate overall scores
            result.setOverallQualityScore(calculateOverallQuality(individualAnalyses));
            result.setConditionScore(calculateConditionScore(individualAnalyses));
            result.setFeatureScore(calculateFeatureScore(individualAnalyses));
            result.setMarketabilityScore(calculateMarketability(assessment, vehicle));
            
            // Generate recommendations
            result.setRecommendations(generateRecommendations(result, vehicle));
            
            log.info("Image analysis completed for vehicle: {} with overall quality: {}", 
                    vehicleId, result.getOverallQualityScore());
            
            return result;

        } catch (Exception e) {
            log.error("Error analyzing vehicle images for vehicle {}: {}", vehicleId, e.getMessage(), e);
            throw new RuntimeException("Failed to analyze vehicle images: " + e.getMessage());
        }
    }

    /**
     * Analyze a single vehicle image
     */
    private ImageAnalysis analyzeSingleImage(MultipartFile image, int index, Car vehicle) {
        ImageAnalysis analysis = new ImageAnalysis();
        analysis.setImageIndex(index);
        analysis.setImageName(image.getOriginalFilename());
        analysis.setImageSize(image.getSize());
        analysis.setAnalysisTimestamp(LocalDateTime.now());

        // Simulate computer vision analysis
        // In production, this would call actual AI services
        
        // Determine image type based on analysis
        ImageType imageType = determineImageType(image, index);
        analysis.setImageType(imageType);
        
        // Analyze based on image type
        switch (imageType) {
            case EXTERIOR_FRONT -> analyzeExteriorFront(analysis, vehicle);
            case EXTERIOR_REAR -> analyzeExteriorRear(analysis, vehicle);
            case EXTERIOR_SIDE -> analyzeExteriorSide(analysis, vehicle);
            case INTERIOR -> analyzeInterior(analysis, vehicle);
            case ENGINE -> analyzeEngine(analysis, vehicle);
            case WHEELS -> analyzeWheels(analysis, vehicle);
            case DASHBOARD -> analyzeDashboard(analysis, vehicle);
            default -> analyzeGeneral(analysis, vehicle);
        }
        
        // Calculate confidence score
        analysis.setConfidenceScore(calculateImageConfidence(analysis));
        
        return analysis;
    }

    /**
     * Determine the type of image based on content analysis
     */
    private ImageType determineImageType(MultipartFile image, int index) {
        // Simulate AI-based image classification
        String filename = image.getOriginalFilename();
        if (filename != null) {
            filename = filename.toLowerCase();
            if (filename.contains("front")) return ImageType.EXTERIOR_FRONT;
            if (filename.contains("rear") || filename.contains("back")) return ImageType.EXTERIOR_REAR;
            if (filename.contains("side")) return ImageType.EXTERIOR_SIDE;
            if (filename.contains("interior") || filename.contains("inside")) return ImageType.INTERIOR;
            if (filename.contains("engine")) return ImageType.ENGINE;
            if (filename.contains("wheel") || filename.contains("tire")) return ImageType.WHEELS;
            if (filename.contains("dashboard") || filename.contains("dash")) return ImageType.DASHBOARD;
        }
        
        // Default classification based on position
        return switch (index % 8) {
            case 0 -> ImageType.EXTERIOR_FRONT;
            case 1 -> ImageType.EXTERIOR_REAR;
            case 2 -> ImageType.EXTERIOR_SIDE;
            case 3 -> ImageType.INTERIOR;
            case 4 -> ImageType.ENGINE;
            case 5 -> ImageType.WHEELS;
            case 6 -> ImageType.DASHBOARD;
            default -> ImageType.GENERAL;
        };
    }

    /**
     * Analyze exterior front view
     */
    private void analyzeExteriorFront(ImageAnalysis analysis, Car vehicle) {
        analysis.setDetectedFeatures(Arrays.asList("HEADLIGHTS", "GRILLE", "BUMPER", "LICENSE_PLATE"));
        
        // Simulate condition assessment
        ConditionAssessment condition = new ConditionAssessment();
        condition.setOverallCondition(simulateConditionScore(85, 95));
        condition.setPaintQuality(simulateConditionScore(80, 92));
        condition.setBodyCondition(simulateConditionScore(88, 96));
        condition.setDamageLevel(simulateDamageLevel());
        condition.setWearIndicators(generateWearIndicators("exterior_front"));
        
        analysis.setConditionAssessment(condition);
        
        // Feature detection
        List<FeatureDetection> features = new ArrayList<>();
        features.add(new FeatureDetection("LED_HEADLIGHTS", 
            Math.random() > 0.6, 0.87, "Advanced LED headlight system detected"));
        features.add(new FeatureDetection("FOG_LIGHTS", 
            Math.random() > 0.7, 0.82, "Fog light assembly identified"));
        features.add(new FeatureDetection("CHROME_TRIM", 
            Math.random() > 0.5, 0.79, "Chrome accent trim visible"));
        
        analysis.setFeatureDetections(features);
        
        // Quality metrics
        QualityMetrics metrics = new QualityMetrics();
        metrics.setImageClarity(simulateScore(80, 95));
        metrics.setLightingQuality(simulateScore(75, 90));
        metrics.setAngleQuality(simulateScore(85, 95));
        metrics.setBackgroundQuality(simulateScore(70, 85));
        
        analysis.setQualityMetrics(metrics);
    }

    /**
     * Analyze exterior rear view
     */
    private void analyzeExteriorRear(ImageAnalysis analysis, Car vehicle) {
        analysis.setDetectedFeatures(Arrays.asList("TAILLIGHTS", "BUMPER", "EXHAUST", "LICENSE_PLATE"));
        
        ConditionAssessment condition = new ConditionAssessment();
        condition.setOverallCondition(simulateConditionScore(83, 94));
        condition.setPaintQuality(simulateConditionScore(81, 93));
        condition.setBodyCondition(simulateConditionScore(85, 95));
        condition.setDamageLevel(simulateDamageLevel());
        condition.setWearIndicators(generateWearIndicators("exterior_rear"));
        
        analysis.setConditionAssessment(condition);
        
        List<FeatureDetection> features = new ArrayList<>();
        features.add(new FeatureDetection("LED_TAILLIGHTS", 
            Math.random() > 0.7, 0.89, "LED taillight system identified"));
        features.add(new FeatureDetection("SPOILER", 
            Math.random() > 0.4, 0.75, "Rear spoiler element detected"));
        features.add(new FeatureDetection("PARKING_SENSORS", 
            Math.random() > 0.6, 0.83, "Rear parking sensor system visible"));
        
        analysis.setFeatureDetections(features);
        
        QualityMetrics metrics = new QualityMetrics();
        metrics.setImageClarity(simulateScore(82, 96));
        metrics.setLightingQuality(simulateScore(78, 92));
        metrics.setAngleQuality(simulateScore(80, 90));
        metrics.setBackgroundQuality(simulateScore(72, 88));
        
        analysis.setQualityMetrics(metrics);
    }

    /**
     * Analyze exterior side view
     */
    private void analyzeExteriorSide(ImageAnalysis analysis, Car vehicle) {
        analysis.setDetectedFeatures(Arrays.asList("DOORS", "WINDOWS", "WHEELS", "MIRRORS"));
        
        ConditionAssessment condition = new ConditionAssessment();
        condition.setOverallCondition(simulateConditionScore(84, 95));
        condition.setPaintQuality(simulateConditionScore(82, 94));
        condition.setBodyCondition(simulateConditionScore(86, 96));
        condition.setDamageLevel(simulateDamageLevel());
        condition.setWearIndicators(generateWearIndicators("exterior_side"));
        
        analysis.setConditionAssessment(condition);
        
        List<FeatureDetection> features = new ArrayList<>();
        features.add(new FeatureDetection("ALLOY_WHEELS", 
            Math.random() > 0.8, 0.91, "Premium alloy wheel design detected"));
        features.add(new FeatureDetection("SUNROOF", 
            Math.random() > 0.3, 0.86, "Sunroof panel identified"));
        features.add(new FeatureDetection("SIDE_STEPS", 
            Math.random() > 0.5, 0.77, "Side step protection visible"));
        
        analysis.setFeatureDetections(features);
        
        QualityMetrics metrics = new QualityMetrics();
        metrics.setImageClarity(simulateScore(85, 97));
        metrics.setLightingQuality(simulateScore(80, 94));
        metrics.setAngleQuality(simulateScore(88, 98));
        metrics.setBackgroundQuality(simulateScore(75, 90));
        
        analysis.setQualityMetrics(metrics);
    }

    /**
     * Analyze interior view
     */
    private void analyzeInterior(ImageAnalysis analysis, Car vehicle) {
        analysis.setDetectedFeatures(Arrays.asList("SEATS", "DASHBOARD", "STEERING_WHEEL", "CONSOLE"));
        
        ConditionAssessment condition = new ConditionAssessment();
        condition.setOverallCondition(simulateConditionScore(80, 92));
        condition.setInteriorCondition(simulateConditionScore(78, 90));
        condition.setUpholsteryCondition(simulateConditionScore(82, 94));
        condition.setDamageLevel(simulateDamageLevel());
        condition.setWearIndicators(generateWearIndicators("interior"));
        
        analysis.setConditionAssessment(condition);
        
        List<FeatureDetection> features = new ArrayList<>();
        features.add(new FeatureDetection("LEATHER_SEATS", 
            Math.random() > 0.6, 0.88, "Leather upholstery detected"));
        features.add(new FeatureDetection("NAVIGATION_SYSTEM", 
            Math.random() > 0.7, 0.85, "Infotainment navigation system identified"));
        features.add(new FeatureDetection("PREMIUM_SOUND_SYSTEM", 
            Math.random() > 0.5, 0.79, "Premium audio system components visible"));
        features.add(new FeatureDetection("DUAL_ZONE_CLIMATE", 
            Math.random() > 0.6, 0.82, "Dual-zone climate control detected"));
        
        analysis.setFeatureDetections(features);
        
        QualityMetrics metrics = new QualityMetrics();
        metrics.setImageClarity(simulateScore(78, 92));
        metrics.setLightingQuality(simulateScore(70, 85));
        metrics.setAngleQuality(simulateScore(82, 94));
        metrics.setBackgroundQuality(simulateScore(85, 95));
        
        analysis.setQualityMetrics(metrics);
    }

    /**
     * Analyze engine bay
     */
    private void analyzeEngine(ImageAnalysis analysis, Car vehicle) {
        analysis.setDetectedFeatures(Arrays.asList("ENGINE_BLOCK", "AIR_FILTER", "BATTERY", "FLUIDS"));
        
        ConditionAssessment condition = new ConditionAssessment();
        condition.setOverallCondition(simulateConditionScore(75, 88));
        condition.setEngineCondition(simulateConditionScore(78, 92));
        condition.setMaintenanceIndicators(generateMaintenanceIndicators());
        condition.setDamageLevel(simulateDamageLevel());
        condition.setWearIndicators(generateWearIndicators("engine"));
        
        analysis.setConditionAssessment(condition);
        
        List<FeatureDetection> features = new ArrayList<>();
        features.add(new FeatureDetection("TURBO_ENGINE", 
            Math.random() > 0.4 && vehicle.getEngine().contains("Turbo"), 0.84, "Turbocharged engine system detected"));
        features.add(new FeatureDetection("PERFORMANCE_AIR_FILTER", 
            Math.random() > 0.3, 0.76, "Performance air intake system visible"));
        features.add(new FeatureDetection("ENGINE_COVER", 
            Math.random() > 0.8, 0.91, "Decorative engine cover present"));
        
        analysis.setFeatureDetections(features);
        
        QualityMetrics metrics = new QualityMetrics();
        metrics.setImageClarity(simulateScore(75, 90));
        metrics.setLightingQuality(simulateScore(65, 80));
        metrics.setAngleQuality(simulateScore(70, 85));
        metrics.setBackgroundQuality(simulateScore(60, 75));
        
        analysis.setQualityMetrics(metrics);
    }

    /**
     * Analyze wheel and tire condition
     */
    private void analyzeWheels(ImageAnalysis analysis, Car vehicle) {
        analysis.setDetectedFeatures(Arrays.asList("TIRES", "RIMS", "BRAKE_DISCS", "CALIPERS"));
        
        ConditionAssessment condition = new ConditionAssessment();
        condition.setOverallCondition(simulateConditionScore(82, 95));
        condition.setTireCondition(simulateConditionScore(80, 93));
        condition.setWheelCondition(simulateConditionScore(85, 96));
        condition.setBrakeCondition(simulateConditionScore(78, 90));
        condition.setDamageLevel(simulateDamageLevel());
        condition.setWearIndicators(generateWearIndicators("wheels"));
        
        analysis.setConditionAssessment(condition);
        
        List<FeatureDetection> features = new ArrayList<>();
        features.add(new FeatureDetection("PERFORMANCE_TIRES", 
            Math.random() > 0.5, 0.87, "High-performance tire compound detected"));
        features.add(new FeatureDetection("ALLOY_WHEELS", 
            Math.random() > 0.9, 0.93, "Alloy wheel construction confirmed"));
        features.add(new FeatureDetection("SPORT_BRAKES", 
            Math.random() > 0.4, 0.81, "Sport brake system components visible"));
        
        analysis.setFeatureDetections(features);
        
        QualityMetrics metrics = new QualityMetrics();
        metrics.setImageClarity(simulateScore(85, 96));
        metrics.setLightingQuality(simulateScore(80, 92));
        metrics.setAngleQuality(simulateScore(88, 95));
        metrics.setBackgroundQuality(simulateScore(70, 85));
        
        analysis.setQualityMetrics(metrics);
    }

    /**
     * Analyze dashboard and instrument cluster
     */
    private void analyzeDashboard(ImageAnalysis analysis, Car vehicle) {
        analysis.setDetectedFeatures(Arrays.asList("INSTRUMENT_CLUSTER", "INFOTAINMENT", "CONTROLS", "MILEAGE"));
        
        ConditionAssessment condition = new ConditionAssessment();
        condition.setOverallCondition(simulateConditionScore(83, 94));
        condition.setInteriorCondition(simulateConditionScore(81, 92));
        condition.setElectronicsCondition(simulateConditionScore(85, 95));
        condition.setDamageLevel(simulateDamageLevel());
        condition.setWearIndicators(generateWearIndicators("dashboard"));
        
        analysis.setConditionAssessment(condition);
        
        List<FeatureDetection> features = new ArrayList<>();
        features.add(new FeatureDetection("DIGITAL_CLUSTER", 
            Math.random() > 0.6, 0.89, "Digital instrument cluster detected"));
        features.add(new FeatureDetection("TOUCHSCREEN_INFOTAINMENT", 
            Math.random() > 0.8, 0.92, "Touchscreen infotainment system identified"));
        features.add(new FeatureDetection("HEADS_UP_DISPLAY", 
            Math.random() > 0.2, 0.85, "Heads-up display projection visible"));
        
        analysis.setFeatureDetections(features);
        
        // Extract mileage if visible
        analysis.setExtractedMileage(extractMileageFromDashboard());
        
        QualityMetrics metrics = new QualityMetrics();
        metrics.setImageClarity(simulateScore(80, 94));
        metrics.setLightingQuality(simulateScore(75, 88));
        metrics.setAngleQuality(simulateScore(85, 95));
        metrics.setBackgroundQuality(simulateScore(90, 98));
        
        analysis.setQualityMetrics(metrics);
    }

    /**
     * General image analysis for unspecified image types
     */
    private void analyzeGeneral(ImageAnalysis analysis, Car vehicle) {
        analysis.setDetectedFeatures(Arrays.asList("VEHICLE", "GENERAL_VIEW"));
        
        ConditionAssessment condition = new ConditionAssessment();
        condition.setOverallCondition(simulateConditionScore(82, 93));
        condition.setDamageLevel(simulateDamageLevel());
        condition.setWearIndicators(generateWearIndicators("general"));
        
        analysis.setConditionAssessment(condition);
        
        List<FeatureDetection> features = new ArrayList<>();
        features.add(new FeatureDetection("VEHICLE_PRESENT", 
            true, 0.95, "Vehicle successfully detected in image"));
        
        analysis.setFeatureDetections(features);
        
        QualityMetrics metrics = new QualityMetrics();
        metrics.setImageClarity(simulateScore(75, 90));
        metrics.setLightingQuality(simulateScore(70, 85));
        metrics.setAngleQuality(simulateScore(80, 92));
        metrics.setBackgroundQuality(simulateScore(75, 88));
        
        analysis.setQualityMetrics(metrics);
    }

    /**
     * Generate comprehensive vehicle assessment
     */
    private VehicleAssessment generateVehicleAssessment(List<ImageAnalysis> analyses, Car vehicle) {
        VehicleAssessment assessment = new VehicleAssessment();
        assessment.setVehicleId(vehicle.getId());
        assessment.setAssessmentDate(LocalDateTime.now());
        
        // Overall condition scoring
        double avgCondition = analyses.stream()
            .mapToDouble(a -> a.getConditionAssessment().getOverallCondition())
            .average()
            .orElse(80.0);
        assessment.setOverallConditionScore(BigDecimal.valueOf(avgCondition).setScale(2, RoundingMode.HALF_UP));
        
        // Exterior assessment
        List<ImageAnalysis> exteriorAnalyses = analyses.stream()
            .filter(a -> isExteriorImage(a.getImageType()))
            .toList();
        if (!exteriorAnalyses.isEmpty()) {
            ExteriorAssessment exterior = generateExteriorAssessment(exteriorAnalyses);
            assessment.setExteriorAssessment(exterior);
        }
        
        // Interior assessment
        List<ImageAnalysis> interiorAnalyses = analyses.stream()
            .filter(a -> isInteriorImage(a.getImageType()))
            .toList();
        if (!interiorAnalyses.isEmpty()) {
            InteriorAssessment interior = generateInteriorAssessment(interiorAnalyses);
            assessment.setInteriorAssessment(interior);
        }
        
        // Technical assessment
        List<ImageAnalysis> technicalAnalyses = analyses.stream()
            .filter(a -> isTechnicalImage(a.getImageType()))
            .toList();
        if (!technicalAnalyses.isEmpty()) {
            TechnicalAssessment technical = generateTechnicalAssessment(technicalAnalyses);
            assessment.setTechnicalAssessment(technical);
        }
        
        // Market impact assessment
        assessment.setMarketImpactAssessment(generateMarketImpactAssessment(assessment, vehicle));
        
        // Risk factors
        assessment.setRiskFactors(identifyRiskFactors(analyses));
        
        // Value indicators
        assessment.setValueIndicators(identifyValueIndicators(analyses));
        
        // Authenticity score
        assessment.setAuthenticityScore(calculateAuthenticityScore(analyses, vehicle));
        
        return assessment;
    }

    /**
     * Calculate overall quality score
     */
    private double calculateOverallQuality(List<ImageAnalysis> analyses) {
        double conditionWeight = 0.4;
        double clarityWeight = 0.3;
        double featureWeight = 0.2;
        double completenessWeight = 0.1;
        
        double avgCondition = analyses.stream()
            .mapToDouble(a -> a.getConditionAssessment().getOverallCondition())
            .average()
            .orElse(80.0);
        
        double avgClarity = analyses.stream()
            .mapToDouble(a -> a.getQualityMetrics().getImageClarity())
            .average()
            .orElse(80.0);
        
        double featureScore = analyses.stream()
            .flatMap(a -> a.getFeatureDetections().stream())
            .mapToDouble(f -> f.isDetected() ? f.getConfidence() * 100 : 50.0)
            .average()
            .orElse(75.0);
        
        double completenessScore = Math.min(analyses.size() * 12.5, 100); // Up to 8 images for full score
        
        return (avgCondition * conditionWeight + 
                avgClarity * clarityWeight + 
                featureScore * featureWeight + 
                completenessScore * completenessWeight);
    }

    /**
     * Generate recommendations based on analysis
     */
    private List<String> generateRecommendations(ImageAnalysisResult result, Car vehicle) {
        List<String> recommendations = new ArrayList<>();
        
        // Image quality recommendations
        if (result.getOverallQualityScore() < 75) {
            recommendations.add("📷 Consider retaking photos with better lighting and angles");
        }
        
        // Condition-based recommendations
        if (result.getConditionScore() < 70) {
            recommendations.add("🔧 Address visible wear and damage before listing");
        }
        
        // Feature highlighting recommendations
        if (result.getFeatureScore() > 80) {
            recommendations.add("⭐ Highlight premium features detected in your listing description");
        }
        
        // Marketability recommendations
        if (result.getMarketabilityScore() > 85) {
            recommendations.add("💰 Your vehicle shows excellent market appeal based on visual analysis");
        } else if (result.getMarketabilityScore() < 65) {
            recommendations.add("📈 Consider professional detailing to improve market presentation");
        }
        
        // Specific feature recommendations
        long premiumFeatures = result.getImageAnalyses().stream()
            .flatMap(a -> a.getFeatureDetections().stream())
            .filter(f -> f.isDetected() && isPremiumFeature(f.getFeatureName()))
            .count();
        
        if (premiumFeatures > 3) {
            recommendations.add("🏆 Emphasize luxury features in your marketing materials");
        }
        
        // Image completeness recommendations
        if (result.getTotalImages() < 6) {
            recommendations.add("📸 Add more images showing different angles and interior details");
        }
        
        return recommendations;
    }

    /**
     * Batch process multiple vehicles' images asynchronously
     */
    @Async("mlTaskExecutor")
    public CompletableFuture<List<ImageAnalysisResult>> batchAnalyzeVehicles(List<Long> vehicleIds) {
        try {
            log.info("Starting batch image analysis for {} vehicles", vehicleIds.size());
            
            List<ImageAnalysisResult> results = new ArrayList<>();
            
            for (Long vehicleId : vehicleIds) {
                try {
                    // Simulate batch processing
                    ImageAnalysisResult result = simulateBatchAnalysis(vehicleId);
                    results.add(result);
                } catch (Exception e) {
                    log.error("Error analyzing vehicle {}: {}", vehicleId, e.getMessage());
                }
            }
            
            log.info("Completed batch image analysis for {} vehicles", results.size());
            return CompletableFuture.completedFuture(results);
            
        } catch (Exception e) {
            log.error("Error in batch image analysis: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Get cached image analysis results
     */
    @Cacheable(value = "imageAnalysis", key = "#vehicleId")
    public ImageAnalysisResult getCachedAnalysis(Long vehicleId) {
        return simulateBatchAnalysis(vehicleId);
    }

    // Helper methods for simulation and calculations

    private double simulateConditionScore(int min, int max) {
        return min + Math.random() * (max - min);
    }

    private double simulateScore(int min, int max) {
        return min + Math.random() * (max - min);
    }

    private DamageLevel simulateDamageLevel() {
        double rand = Math.random();
        if (rand < 0.7) return DamageLevel.NONE;
        else if (rand < 0.9) return DamageLevel.MINOR;
        else if (rand < 0.98) return DamageLevel.MODERATE;
        else return DamageLevel.MAJOR;
    }

    private List<String> generateWearIndicators(String category) {
        Map<String, List<String>> categoryIndicators = Map.of(
            "exterior_front", Arrays.asList("Paint fade", "Headlight clarity", "Bumper scuffs"),
            "exterior_rear", Arrays.asList("Taillight condition", "Exhaust tips", "Bumper wear"),
            "exterior_side", Arrays.asList("Door dents", "Panel alignment", "Trim condition"),
            "interior", Arrays.asList("Seat wear", "Dashboard cracks", "Control wear"),
            "engine", Arrays.asList("Belt condition", "Fluid levels", "Component wear"),
            "wheels", Arrays.asList("Tire tread", "Rim condition", "Brake wear"),
            "dashboard", Arrays.asList("Screen clarity", "Button wear", "Surface condition")
        );
        
        return categoryIndicators.getOrDefault(category, Arrays.asList("General wear"));
    }

    private List<String> generateMaintenanceIndicators() {
        return Arrays.asList("Regular maintenance evident", "Clean components", "Proper fluid levels");
    }

    private Long extractMileageFromDashboard() {
        // Simulate OCR extraction of mileage from dashboard
        return (long) (20000 + Math.random() * 80000);
    }

    private double calculateImageConfidence(ImageAnalysis analysis) {
        return analysis.getQualityMetrics().getImageClarity() * 
               (1.0 + analysis.getFeatureDetections().size() * 0.05);
    }

    private double calculateConditionScore(List<ImageAnalysis> analyses) {
        return analyses.stream()
            .mapToDouble(a -> a.getConditionAssessment().getOverallCondition())
            .average()
            .orElse(80.0);
    }

    private double calculateFeatureScore(List<ImageAnalysis> analyses) {
        return analyses.stream()
            .flatMap(a -> a.getFeatureDetections().stream())
            .filter(FeatureDetection::isDetected)
            .mapToDouble(f -> f.getConfidence() * 100)
            .average()
            .orElse(75.0);
    }

    private double calculateMarketability(VehicleAssessment assessment, Car vehicle) {
        double baseScore = assessment.getOverallConditionScore().doubleValue();
        
        // Adjust based on vehicle characteristics
        if (vehicle.getYear() != null && vehicle.getYear() > 2020) baseScore += 5;
        if (vehicle.getMileage() != null && vehicle.getMileage() < 30000) baseScore += 5;
        if (isPremiumBrand(vehicle.getMake())) baseScore += 3;
        
        return Math.min(baseScore, 100.0);
    }

    private boolean isExteriorImage(ImageType type) {
        return type == ImageType.EXTERIOR_FRONT || type == ImageType.EXTERIOR_REAR || 
               type == ImageType.EXTERIOR_SIDE || type == ImageType.WHEELS;
    }

    private boolean isInteriorImage(ImageType type) {
        return type == ImageType.INTERIOR || type == ImageType.DASHBOARD;
    }

    private boolean isTechnicalImage(ImageType type) {
        return type == ImageType.ENGINE || type == ImageType.WHEELS;
    }

    private boolean isPremiumFeature(String featureName) {
        return Arrays.asList("LED_HEADLIGHTS", "LEATHER_SEATS", "NAVIGATION_SYSTEM",
                           "PREMIUM_SOUND_SYSTEM", "SUNROOF", "PERFORMANCE_TIRES").contains(featureName);
    }

    private boolean isPremiumBrand(String make) {
        return Arrays.asList("BMW", "Mercedes-Benz", "Audi", "Lexus", "Acura", "Infiniti").contains(make);
    }

    // Additional assessment methods
    
    private ExteriorAssessment generateExteriorAssessment(List<ImageAnalysis> exteriorAnalyses) {
        ExteriorAssessment assessment = new ExteriorAssessment();
        
        double avgPaintQuality = exteriorAnalyses.stream()
            .mapToDouble(a -> a.getConditionAssessment().getPaintQuality())
            .average()
            .orElse(85.0);
        assessment.setPaintQuality(BigDecimal.valueOf(avgPaintQuality).setScale(2, RoundingMode.HALF_UP));
        
        double avgBodyCondition = exteriorAnalyses.stream()
            .mapToDouble(a -> a.getConditionAssessment().getBodyCondition())
            .average()
            .orElse(87.0);
        assessment.setBodyCondition(BigDecimal.valueOf(avgBodyCondition).setScale(2, RoundingMode.HALF_UP));
        
        assessment.setDetectedFeatures(exteriorAnalyses.stream()
            .flatMap(a -> a.getFeatureDetections().stream())
            .filter(FeatureDetection::isDetected)
            .map(FeatureDetection::getFeatureName)
            .collect(Collectors.toSet()));
        
        assessment.setOverallExteriorScore(
            BigDecimal.valueOf((avgPaintQuality + avgBodyCondition) / 2)
                      .setScale(2, RoundingMode.HALF_UP));
        
        return assessment;
    }

    private InteriorAssessment generateInteriorAssessment(List<ImageAnalysis> interiorAnalyses) {
        InteriorAssessment assessment = new InteriorAssessment();
        
        double avgInteriorCondition = interiorAnalyses.stream()
            .mapToDouble(a -> a.getConditionAssessment().getInteriorCondition())
            .average()
            .orElse(82.0);
        assessment.setInteriorCondition(BigDecimal.valueOf(avgInteriorCondition).setScale(2, RoundingMode.HALF_UP));
        
        double avgUpholsteryCondition = interiorAnalyses.stream()
            .mapToDouble(a -> a.getConditionAssessment().getUpholsteryCondition())
            .average()
            .orElse(85.0);
        assessment.setUpholsteryCondition(BigDecimal.valueOf(avgUpholsteryCondition).setScale(2, RoundingMode.HALF_UP));
        
        assessment.setDetectedFeatures(interiorAnalyses.stream()
            .flatMap(a -> a.getFeatureDetections().stream())
            .filter(FeatureDetection::isDetected)
            .map(FeatureDetection::getFeatureName)
            .collect(Collectors.toSet()));
        
        assessment.setOverallInteriorScore(
            BigDecimal.valueOf((avgInteriorCondition + avgUpholsteryCondition) / 2)
                      .setScale(2, RoundingMode.HALF_UP));
        
        return assessment;
    }

    private TechnicalAssessment generateTechnicalAssessment(List<ImageAnalysis> technicalAnalyses) {
        TechnicalAssessment assessment = new TechnicalAssessment();
        
        double avgEngineCondition = technicalAnalyses.stream()
            .filter(a -> a.getImageType() == ImageType.ENGINE)
            .mapToDouble(a -> a.getConditionAssessment().getEngineCondition())
            .average()
            .orElse(80.0);
        assessment.setEngineCondition(BigDecimal.valueOf(avgEngineCondition).setScale(2, RoundingMode.HALF_UP));
        
        double avgWheelCondition = technicalAnalyses.stream()
            .filter(a -> a.getImageType() == ImageType.WHEELS)
            .mapToDouble(a -> a.getConditionAssessment().getWheelCondition())
            .average()
            .orElse(85.0);
        assessment.setWheelCondition(BigDecimal.valueOf(avgWheelCondition).setScale(2, RoundingMode.HALF_UP));
        
        assessment.setTechnicalFeatures(technicalAnalyses.stream()
            .flatMap(a -> a.getFeatureDetections().stream())
            .filter(FeatureDetection::isDetected)
            .map(FeatureDetection::getFeatureName)
            .collect(Collectors.toSet()));
        
        assessment.setOverallTechnicalScore(
            BigDecimal.valueOf((avgEngineCondition + avgWheelCondition) / 2)
                      .setScale(2, RoundingMode.HALF_UP));
        
        return assessment;
    }

    private MarketImpactAssessment generateMarketImpactAssessment(VehicleAssessment assessment, Car vehicle) {
        MarketImpactAssessment impact = new MarketImpactAssessment();
        
        double baseScore = assessment.getOverallConditionScore().doubleValue();
        
        // Adjust for visual appeal factors
        if (assessment.getExteriorAssessment() != null && 
            assessment.getExteriorAssessment().getPaintQuality().doubleValue() > 90) {
            baseScore += 5;
        }
        
        if (assessment.getInteriorAssessment() != null && 
            assessment.getInteriorAssessment().getInteriorCondition().doubleValue() > 85) {
            baseScore += 3;
        }
        
        impact.setVisualAppealScore(BigDecimal.valueOf(Math.min(baseScore, 100))
                                               .setScale(2, RoundingMode.HALF_UP));
        
        impact.setMarketReadinessScore(BigDecimal.valueOf(baseScore * 0.9)
                                                  .setScale(2, RoundingMode.HALF_UP));
        
        impact.setPhotogenicScore(BigDecimal.valueOf(baseScore + Math.random() * 10 - 5)
                                             .setScale(2, RoundingMode.HALF_UP));
        
        return impact;
    }

    private List<String> identifyRiskFactors(List<ImageAnalysis> analyses) {
        List<String> risks = new ArrayList<>();
        
        // Check for damage
        long majorDamage = analyses.stream()
            .filter(a -> a.getConditionAssessment().getDamageLevel() == DamageLevel.MAJOR)
            .count();
        if (majorDamage > 0) {
            risks.add("Major damage detected in " + majorDamage + " image(s)");
        }
        
        // Check for poor image quality
        long poorQuality = analyses.stream()
            .filter(a -> a.getQualityMetrics().getImageClarity() < 60)
            .count();
        if (poorQuality > 0) {
            risks.add("Poor image quality may affect buyer interest");
        }
        
        // Check for missing critical images
        boolean hasFrontView = analyses.stream().anyMatch(a -> a.getImageType() == ImageType.EXTERIOR_FRONT);
        boolean hasInteriorView = analyses.stream().anyMatch(a -> a.getImageType() == ImageType.INTERIOR);
        
        if (!hasFrontView) risks.add("Missing front exterior view");
        if (!hasInteriorView) risks.add("Missing interior view");
        
        return risks;
    }

    private List<String> identifyValueIndicators(List<ImageAnalysis> analyses) {
        List<String> values = new ArrayList<>();
        
        // Check for premium features
        long premiumFeatures = analyses.stream()
            .flatMap(a -> a.getFeatureDetections().stream())
            .filter(f -> f.isDetected() && isPremiumFeature(f.getFeatureName()))
            .count();
        
        if (premiumFeatures > 2) {
            values.add("Multiple premium features detected (" + premiumFeatures + ")");
        }
        
        // Check for excellent condition
        double avgCondition = analyses.stream()
            .mapToDouble(a -> a.getConditionAssessment().getOverallCondition())
            .average()
            .orElse(0);
        
        if (avgCondition > 90) {
            values.add("Exceptional overall condition (90%+)");
        }
        
        // Check for comprehensive documentation
        if (analyses.size() > 6) {
            values.add("Comprehensive visual documentation");
        }
        
        return values;
    }

    private double calculateAuthenticityScore(List<ImageAnalysis> analyses, Car vehicle) {
        double baseScore = 85.0;
        
        // Boost for image completeness
        baseScore += Math.min(analyses.size() * 2, 10);
        
        // Boost for feature consistency
        Set<String> detectedFeatures = analyses.stream()
            .flatMap(a -> a.getFeatureDetections().stream())
            .filter(FeatureDetection::isDetected)
            .map(FeatureDetection::getFeatureName)
            .collect(Collectors.toSet());
        
        baseScore += detectedFeatures.size() * 0.5;
        
        return Math.min(baseScore, 100.0);
    }

    private ImageAnalysisResult simulateBatchAnalysis(Long vehicleId) {
        // Simulate analysis result for batch processing
        ImageAnalysisResult result = new ImageAnalysisResult();
        result.setVehicleId(vehicleId);
        result.setAnalysisTimestamp(LocalDateTime.now());
        result.setTotalImages((int) (4 + Math.random() * 4)); // 4-8 images
        result.setOverallQualityScore(75 + Math.random() * 20);
        result.setConditionScore(80 + Math.random() * 15);
        result.setFeatureScore(70 + Math.random() * 25);
        result.setMarketabilityScore(75 + Math.random() * 20);
        
        result.setRecommendations(Arrays.asList(
            "Image quality is good for market presentation",
            "Consider highlighting detected premium features"
        ));
        
        return result;
    }

    // Enums and Data Classes

    public enum ImageType {
        EXTERIOR_FRONT,
        EXTERIOR_REAR, 
        EXTERIOR_SIDE,
        INTERIOR,
        ENGINE,
        WHEELS,
        DASHBOARD,
        GENERAL
    }

    public enum DamageLevel {
        NONE,
        MINOR,
        MODERATE,
        MAJOR
    }

    // Data classes for structured results
    
    public static class ImageAnalysisResult {
        private Long vehicleId;
        private LocalDateTime analysisTimestamp;
        private int totalImages;
        private List<ImageAnalysis> imageAnalyses;
        private VehicleAssessment vehicleAssessment;
        private double overallQualityScore;
        private double conditionScore;
        private double featureScore;
        private double marketabilityScore;
        private List<String> recommendations;

        // Getters and setters
        public Long getVehicleId() { return vehicleId; }
        public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
        
        public LocalDateTime getAnalysisTimestamp() { return analysisTimestamp; }
        public void setAnalysisTimestamp(LocalDateTime analysisTimestamp) { this.analysisTimestamp = analysisTimestamp; }
        
        public int getTotalImages() { return totalImages; }
        public void setTotalImages(int totalImages) { this.totalImages = totalImages; }
        
        public List<ImageAnalysis> getImageAnalyses() { return imageAnalyses; }
        public void setImageAnalyses(List<ImageAnalysis> imageAnalyses) { this.imageAnalyses = imageAnalyses; }
        
        public VehicleAssessment getVehicleAssessment() { return vehicleAssessment; }
        public void setVehicleAssessment(VehicleAssessment vehicleAssessment) { this.vehicleAssessment = vehicleAssessment; }
        
        public double getOverallQualityScore() { return overallQualityScore; }
        public void setOverallQualityScore(double overallQualityScore) { this.overallQualityScore = overallQualityScore; }
        
        public double getConditionScore() { return conditionScore; }
        public void setConditionScore(double conditionScore) { this.conditionScore = conditionScore; }
        
        public double getFeatureScore() { return featureScore; }
        public void setFeatureScore(double featureScore) { this.featureScore = featureScore; }
        
        public double getMarketabilityScore() { return marketabilityScore; }
        public void setMarketabilityScore(double marketabilityScore) { this.marketabilityScore = marketabilityScore; }
        
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    }

    public static class ImageAnalysis {
        private int imageIndex;
        private String imageName;
        private long imageSize;
        private ImageType imageType;
        private LocalDateTime analysisTimestamp;
        private List<String> detectedFeatures;
        private ConditionAssessment conditionAssessment;
        private List<FeatureDetection> featureDetections;
        private QualityMetrics qualityMetrics;
        private double confidenceScore;
        private Long extractedMileage;

        // Getters and setters
        public int getImageIndex() { return imageIndex; }
        public void setImageIndex(int imageIndex) { this.imageIndex = imageIndex; }
        
        public String getImageName() { return imageName; }
        public void setImageName(String imageName) { this.imageName = imageName; }
        
        public long getImageSize() { return imageSize; }
        public void setImageSize(long imageSize) { this.imageSize = imageSize; }
        
        public ImageType getImageType() { return imageType; }
        public void setImageType(ImageType imageType) { this.imageType = imageType; }
        
        public LocalDateTime getAnalysisTimestamp() { return analysisTimestamp; }
        public void setAnalysisTimestamp(LocalDateTime analysisTimestamp) { this.analysisTimestamp = analysisTimestamp; }
        
        public List<String> getDetectedFeatures() { return detectedFeatures; }
        public void setDetectedFeatures(List<String> detectedFeatures) { this.detectedFeatures = detectedFeatures; }
        
        public ConditionAssessment getConditionAssessment() { return conditionAssessment; }
        public void setConditionAssessment(ConditionAssessment conditionAssessment) { this.conditionAssessment = conditionAssessment; }
        
        public List<FeatureDetection> getFeatureDetections() { return featureDetections; }
        public void setFeatureDetections(List<FeatureDetection> featureDetections) { this.featureDetections = featureDetections; }
        
        public QualityMetrics getQualityMetrics() { return qualityMetrics; }
        public void setQualityMetrics(QualityMetrics qualityMetrics) { this.qualityMetrics = qualityMetrics; }
        
        public double getConfidenceScore() { return confidenceScore; }
        public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
        
        public Long getExtractedMileage() { return extractedMileage; }
        public void setExtractedMileage(Long extractedMileage) { this.extractedMileage = extractedMileage; }
    }

    public static class ConditionAssessment {
        private double overallCondition;
        private double paintQuality;
        private double bodyCondition;
        private double interiorCondition;
        private double upholsteryCondition;
        private double engineCondition;
        private double wheelCondition;
        private double tireCondition;
        private double brakeCondition;
        private double electronicsCondition;
        private DamageLevel damageLevel;
        private List<String> wearIndicators;
        private List<String> maintenanceIndicators;

        // Getters and setters
        public double getOverallCondition() { return overallCondition; }
        public void setOverallCondition(double overallCondition) { this.overallCondition = overallCondition; }
        
        public double getPaintQuality() { return paintQuality; }
        public void setPaintQuality(double paintQuality) { this.paintQuality = paintQuality; }
        
        public double getBodyCondition() { return bodyCondition; }
        public void setBodyCondition(double bodyCondition) { this.bodyCondition = bodyCondition; }
        
        public double getInteriorCondition() { return interiorCondition; }
        public void setInteriorCondition(double interiorCondition) { this.interiorCondition = interiorCondition; }
        
        public double getUpholsteryCondition() { return upholsteryCondition; }
        public void setUpholsteryCondition(double upholsteryCondition) { this.upholsteryCondition = upholsteryCondition; }
        
        public double getEngineCondition() { return engineCondition; }
        public void setEngineCondition(double engineCondition) { this.engineCondition = engineCondition; }
        
        public double getWheelCondition() { return wheelCondition; }
        public void setWheelCondition(double wheelCondition) { this.wheelCondition = wheelCondition; }
        
        public double getTireCondition() { return tireCondition; }
        public void setTireCondition(double tireCondition) { this.tireCondition = tireCondition; }
        
        public double getBrakeCondition() { return brakeCondition; }
        public void setBrakeCondition(double brakeCondition) { this.brakeCondition = brakeCondition; }
        
        public double getElectronicsCondition() { return electronicsCondition; }
        public void setElectronicsCondition(double electronicsCondition) { this.electronicsCondition = electronicsCondition; }
        
        public DamageLevel getDamageLevel() { return damageLevel; }
        public void setDamageLevel(DamageLevel damageLevel) { this.damageLevel = damageLevel; }
        
        public List<String> getWearIndicators() { return wearIndicators; }
        public void setWearIndicators(List<String> wearIndicators) { this.wearIndicators = wearIndicators; }
        
        public List<String> getMaintenanceIndicators() { return maintenanceIndicators; }
        public void setMaintenanceIndicators(List<String> maintenanceIndicators) { this.maintenanceIndicators = maintenanceIndicators; }
    }

    public static class FeatureDetection {
        private String featureName;
        private boolean detected;
        private double confidence;
        private String description;

        public FeatureDetection(String featureName, boolean detected, double confidence, String description) {
            this.featureName = featureName;
            this.detected = detected;
            this.confidence = confidence;
            this.description = description;
        }

        // Getters and setters
        public String getFeatureName() { return featureName; }
        public void setFeatureName(String featureName) { this.featureName = featureName; }
        
        public boolean isDetected() { return detected; }
        public void setDetected(boolean detected) { this.detected = detected; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class QualityMetrics {
        private double imageClarity;
        private double lightingQuality;
        private double angleQuality;
        private double backgroundQuality;

        // Getters and setters
        public double getImageClarity() { return imageClarity; }
        public void setImageClarity(double imageClarity) { this.imageClarity = imageClarity; }
        
        public double getLightingQuality() { return lightingQuality; }
        public void setLightingQuality(double lightingQuality) { this.lightingQuality = lightingQuality; }
        
        public double getAngleQuality() { return angleQuality; }
        public void setAngleQuality(double angleQuality) { this.angleQuality = angleQuality; }
        
        public double getBackgroundQuality() { return backgroundQuality; }
        public void setBackgroundQuality(double backgroundQuality) { this.backgroundQuality = backgroundQuality; }
    }

    public static class VehicleAssessment {
        private Long vehicleId;
        private LocalDateTime assessmentDate;
        private BigDecimal overallConditionScore;
        private ExteriorAssessment exteriorAssessment;
        private InteriorAssessment interiorAssessment;
        private TechnicalAssessment technicalAssessment;
        private MarketImpactAssessment marketImpactAssessment;
        private List<String> riskFactors;
        private List<String> valueIndicators;
        private double authenticityScore;

        // Getters and setters
        public Long getVehicleId() { return vehicleId; }
        public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
        
        public LocalDateTime getAssessmentDate() { return assessmentDate; }
        public void setAssessmentDate(LocalDateTime assessmentDate) { this.assessmentDate = assessmentDate; }
        
        public BigDecimal getOverallConditionScore() { return overallConditionScore; }
        public void setOverallConditionScore(BigDecimal overallConditionScore) { this.overallConditionScore = overallConditionScore; }
        
        public ExteriorAssessment getExteriorAssessment() { return exteriorAssessment; }
        public void setExteriorAssessment(ExteriorAssessment exteriorAssessment) { this.exteriorAssessment = exteriorAssessment; }
        
        public InteriorAssessment getInteriorAssessment() { return interiorAssessment; }
        public void setInteriorAssessment(InteriorAssessment interiorAssessment) { this.interiorAssessment = interiorAssessment; }
        
        public TechnicalAssessment getTechnicalAssessment() { return technicalAssessment; }
        public void setTechnicalAssessment(TechnicalAssessment technicalAssessment) { this.technicalAssessment = technicalAssessment; }
        
        public MarketImpactAssessment getMarketImpactAssessment() { return marketImpactAssessment; }
        public void setMarketImpactAssessment(MarketImpactAssessment marketImpactAssessment) { this.marketImpactAssessment = marketImpactAssessment; }
        
        public List<String> getRiskFactors() { return riskFactors; }
        public void setRiskFactors(List<String> riskFactors) { this.riskFactors = riskFactors; }
        
        public List<String> getValueIndicators() { return valueIndicators; }
        public void setValueIndicators(List<String> valueIndicators) { this.valueIndicators = valueIndicators; }
        
        public double getAuthenticityScore() { return authenticityScore; }
        public void setAuthenticityScore(double authenticityScore) { this.authenticityScore = authenticityScore; }
    }

    public static class ExteriorAssessment {
        private BigDecimal paintQuality;
        private BigDecimal bodyCondition;
        private Set<String> detectedFeatures;
        private BigDecimal overallExteriorScore;

        // Getters and setters
        public BigDecimal getPaintQuality() { return paintQuality; }
        public void setPaintQuality(BigDecimal paintQuality) { this.paintQuality = paintQuality; }
        
        public BigDecimal getBodyCondition() { return bodyCondition; }
        public void setBodyCondition(BigDecimal bodyCondition) { this.bodyCondition = bodyCondition; }
        
        public Set<String> getDetectedFeatures() { return detectedFeatures; }
        public void setDetectedFeatures(Set<String> detectedFeatures) { this.detectedFeatures = detectedFeatures; }
        
        public BigDecimal getOverallExteriorScore() { return overallExteriorScore; }
        public void setOverallExteriorScore(BigDecimal overallExteriorScore) { this.overallExteriorScore = overallExteriorScore; }
    }

    public static class InteriorAssessment {
        private BigDecimal interiorCondition;
        private BigDecimal upholsteryCondition;
        private Set<String> detectedFeatures;
        private BigDecimal overallInteriorScore;

        // Getters and setters
        public BigDecimal getInteriorCondition() { return interiorCondition; }
        public void setInteriorCondition(BigDecimal interiorCondition) { this.interiorCondition = interiorCondition; }
        
        public BigDecimal getUpholsteryCondition() { return upholsteryCondition; }
        public void setUpholsteryCondition(BigDecimal upholsteryCondition) { this.upholsteryCondition = upholsteryCondition; }
        
        public Set<String> getDetectedFeatures() { return detectedFeatures; }
        public void setDetectedFeatures(Set<String> detectedFeatures) { this.detectedFeatures = detectedFeatures; }
        
        public BigDecimal getOverallInteriorScore() { return overallInteriorScore; }
        public void setOverallInteriorScore(BigDecimal overallInteriorScore) { this.overallInteriorScore = overallInteriorScore; }
    }

    public static class TechnicalAssessment {
        private BigDecimal engineCondition;
        private BigDecimal wheelCondition;
        private Set<String> technicalFeatures;
        private BigDecimal overallTechnicalScore;

        // Getters and setters
        public BigDecimal getEngineCondition() { return engineCondition; }
        public void setEngineCondition(BigDecimal engineCondition) { this.engineCondition = engineCondition; }
        
        public BigDecimal getWheelCondition() { return wheelCondition; }
        public void setWheelCondition(BigDecimal wheelCondition) { this.wheelCondition = wheelCondition; }
        
        public Set<String> getTechnicalFeatures() { return technicalFeatures; }
        public void setTechnicalFeatures(Set<String> technicalFeatures) { this.technicalFeatures = technicalFeatures; }
        
        public BigDecimal getOverallTechnicalScore() { return overallTechnicalScore; }
        public void setOverallTechnicalScore(BigDecimal overallTechnicalScore) { this.overallTechnicalScore = overallTechnicalScore; }
    }

    public static class MarketImpactAssessment {
        private BigDecimal visualAppealScore;
        private BigDecimal marketReadinessScore;
        private BigDecimal photogenicScore;

        // Getters and setters
        public BigDecimal getVisualAppealScore() { return visualAppealScore; }
        public void setVisualAppealScore(BigDecimal visualAppealScore) { this.visualAppealScore = visualAppealScore; }
        
        public BigDecimal getMarketReadinessScore() { return marketReadinessScore; }
        public void setMarketReadinessScore(BigDecimal marketReadinessScore) { this.marketReadinessScore = marketReadinessScore; }
        
        public BigDecimal getPhotogenicScore() { return photogenicScore; }
        public void setPhotogenicScore(BigDecimal photogenicScore) { this.photogenicScore = photogenicScore; }
    }
}
