package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.ApiResponse;
import com.carselling.oldcar.service.ImageRecognitionService;
import com.carselling.oldcar.service.ImageRecognitionService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Image Recognition Controller
 * Provides AI-powered computer vision services for vehicle image analysis
 */
@RestController
@RequestMapping("/api/image-recognition")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Image Recognition", description = "AI-powered vehicle image analysis and feature detection")
public class ImageRecognitionController {

    private final ImageRecognitionService imageRecognitionService;

    /**
     * Analyze vehicle images with AI-powered computer vision
     */
    @PostMapping("/analyze/{vehicleId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Analyze vehicle images", 
               description = "Upload and analyze vehicle images using AI for condition assessment, feature detection, and quality scoring")
    public ResponseEntity<ApiResponse<ImageAnalysisResult>> analyzeVehicleImages(
            @Parameter(description = "Vehicle ID") @PathVariable Long vehicleId,
            @Parameter(description = "Vehicle images to analyze") @RequestParam("images") List<MultipartFile> images) {
        
        try {
            log.info("Starting image analysis for vehicle: {} with {} images", vehicleId, images.size());
            
            // Validate input
            if (images.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("At least one image is required for analysis"));
            }
            
            if (images.size() > 20) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Maximum 20 images allowed per analysis"));
            }
            
            // Validate image files
            for (MultipartFile image : images) {
                if (image.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("Empty image file detected"));
                }
                
                if (image.getSize() > 10 * 1024 * 1024) { // 10MB limit
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("Image file size must be less than 10MB"));
                }
                
                String contentType = image.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("Invalid image format. Only image files are allowed"));
                }
            }
            
            ImageAnalysisResult result = imageRecognitionService.analyzeVehicleImages(images, vehicleId);
            
            return ResponseEntity.ok(ApiResponse.success(result, 
                    "Vehicle image analysis completed successfully"));

        } catch (Exception e) {
            log.error("Error analyzing vehicle images for vehicle {}: {}", vehicleId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to analyze vehicle images: " + e.getMessage()));
        }
    }

    /**
     * Get cached image analysis results
     */
    @GetMapping("/analysis/{vehicleId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get image analysis results", 
               description = "Retrieve previously computed image analysis results for a vehicle")
    public ResponseEntity<ApiResponse<ImageAnalysisResult>> getImageAnalysis(
            @Parameter(description = "Vehicle ID") @PathVariable Long vehicleId) {
        
        try {
            log.info("Retrieving cached image analysis for vehicle: {}", vehicleId);
            
            ImageAnalysisResult result = imageRecognitionService.getCachedAnalysis(vehicleId);
            
            return ResponseEntity.ok(ApiResponse.success(result, 
                    "Image analysis results retrieved successfully"));

        } catch (Exception e) {
            log.error("Error retrieving image analysis for vehicle {}: {}", vehicleId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve image analysis: " + e.getMessage()));
        }
    }

    /**
     * Batch analyze multiple vehicles' images asynchronously
     */
    @PostMapping("/batch-analyze")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DEALER')")
    @Operation(summary = "Batch analyze vehicles", 
               description = "Analyze multiple vehicles' images in batch mode (async processing)")
    public ResponseEntity<ApiResponse<String>> batchAnalyzeVehicles(
            @Parameter(description = "List of vehicle IDs") @RequestBody List<Long> vehicleIds) {
        
        try {
            log.info("Starting batch image analysis for {} vehicles", vehicleIds.size());
            
            if (vehicleIds.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Vehicle ID list cannot be empty"));
            }
            
            if (vehicleIds.size() > 100) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Maximum 100 vehicles allowed per batch"));
            }
            
            CompletableFuture<List<ImageAnalysisResult>> batchFuture = 
                    imageRecognitionService.batchAnalyzeVehicles(vehicleIds);
            
            return ResponseEntity.ok(ApiResponse.success("Batch analysis initiated", 
                    "Batch image analysis is processing in the background for " + vehicleIds.size() + " vehicles"));

        } catch (Exception e) {
            log.error("Error initiating batch image analysis: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to initiate batch analysis: " + e.getMessage()));
        }
    }

    /**
     * Get vehicle condition assessment summary
     */
    @GetMapping("/condition-assessment/{vehicleId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get condition assessment", 
               description = "Get AI-powered vehicle condition assessment based on image analysis")
    public ResponseEntity<ApiResponse<VehicleConditionSummary>> getConditionAssessment(
            @Parameter(description = "Vehicle ID") @PathVariable Long vehicleId) {
        
        try {
            log.info("Generating condition assessment summary for vehicle: {}", vehicleId);
            
            ImageAnalysisResult analysisResult = imageRecognitionService.getCachedAnalysis(vehicleId);
            
            VehicleConditionSummary summary = new VehicleConditionSummary();
            summary.setVehicleId(vehicleId);
            summary.setOverallConditionScore(analysisResult.getConditionScore());
            summary.setQualityScore(analysisResult.getOverallQualityScore());
            summary.setMarketabilityScore(analysisResult.getMarketabilityScore());
            summary.setFeatureScore(analysisResult.getFeatureScore());
            
            // Extract assessment details
            VehicleAssessment assessment = analysisResult.getVehicleAssessment();
            if (assessment != null) {
                summary.setExteriorScore(assessment.getExteriorAssessment() != null ? 
                    assessment.getExteriorAssessment().getOverallExteriorScore().doubleValue() : 0.0);
                summary.setInteriorScore(assessment.getInteriorAssessment() != null ? 
                    assessment.getInteriorAssessment().getOverallInteriorScore().doubleValue() : 0.0);
                summary.setTechnicalScore(assessment.getTechnicalAssessment() != null ? 
                    assessment.getTechnicalAssessment().getOverallTechnicalScore().doubleValue() : 0.0);
                summary.setRiskFactors(assessment.getRiskFactors());
                summary.setValueIndicators(assessment.getValueIndicators());
                summary.setAuthenticityScore(assessment.getAuthenticityScore());
            }
            
            summary.setRecommendations(analysisResult.getRecommendations());
            summary.setLastAnalyzed(analysisResult.getAnalysisTimestamp());
            
            return ResponseEntity.ok(ApiResponse.success(summary, 
                    "Vehicle condition assessment generated successfully"));

        } catch (Exception e) {
            log.error("Error generating condition assessment for vehicle {}: {}", vehicleId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to generate condition assessment: " + e.getMessage()));
        }
    }

    /**
     * Get detected features summary
     */
    @GetMapping("/features/{vehicleId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get detected features", 
               description = "Get AI-detected vehicle features and their confidence scores")
    public ResponseEntity<ApiResponse<VehicleFeaturesSummary>> getDetectedFeatures(
            @Parameter(description = "Vehicle ID") @PathVariable Long vehicleId) {
        
        try {
            log.info("Retrieving detected features for vehicle: {}", vehicleId);
            
            ImageAnalysisResult analysisResult = imageRecognitionService.getCachedAnalysis(vehicleId);
            
            VehicleFeaturesSummary summary = new VehicleFeaturesSummary();
            summary.setVehicleId(vehicleId);
            
            // Collect all detected features
            List<DetectedFeatureSummary> detectedFeatures = analysisResult.getImageAnalyses().stream()
                .flatMap(analysis -> analysis.getFeatureDetections().stream())
                .filter(FeatureDetection::isDetected)
                .map(feature -> {
                    DetectedFeatureSummary featureSummary = new DetectedFeatureSummary();
                    featureSummary.setFeatureName(feature.getFeatureName());
                    featureSummary.setConfidenceScore(feature.getConfidence());
                    featureSummary.setDescription(feature.getDescription());
                    return featureSummary;
                })
                .toList();
            
            summary.setDetectedFeatures(detectedFeatures);
            summary.setTotalFeaturesDetected(detectedFeatures.size());
            
            // Categorize features
            summary.setExteriorFeatures(detectedFeatures.stream()
                .filter(f -> isExteriorFeature(f.getFeatureName()))
                .toList());
            summary.setInteriorFeatures(detectedFeatures.stream()
                .filter(f -> isInteriorFeature(f.getFeatureName()))
                .toList());
            summary.setSafetyFeatures(detectedFeatures.stream()
                .filter(f -> isSafetyFeature(f.getFeatureName()))
                .toList());
            summary.setTechnicalFeatures(detectedFeatures.stream()
                .filter(f -> isTechnicalFeature(f.getFeatureName()))
                .toList());
            
            summary.setLastAnalyzed(analysisResult.getAnalysisTimestamp());
            
            return ResponseEntity.ok(ApiResponse.success(summary, 
                    "Vehicle features retrieved successfully"));

        } catch (Exception e) {
            log.error("Error retrieving features for vehicle {}: {}", vehicleId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve vehicle features: " + e.getMessage()));
        }
    }

    /**
     * Get image quality analysis
     */
    @GetMapping("/image-quality/{vehicleId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get image quality analysis", 
               description = "Get detailed image quality metrics and recommendations")
    public ResponseEntity<ApiResponse<ImageQualitySummary>> getImageQuality(
            @Parameter(description = "Vehicle ID") @PathVariable Long vehicleId) {
        
        try {
            log.info("Analyzing image quality for vehicle: {}", vehicleId);
            
            ImageAnalysisResult analysisResult = imageRecognitionService.getCachedAnalysis(vehicleId);
            
            ImageQualitySummary summary = new ImageQualitySummary();
            summary.setVehicleId(vehicleId);
            summary.setTotalImages(analysisResult.getTotalImages());
            
            // Calculate average quality metrics
            List<ImageAnalysis> analyses = analysisResult.getImageAnalyses();
            if (!analyses.isEmpty()) {
                summary.setAverageClarity(analyses.stream()
                    .mapToDouble(a -> a.getQualityMetrics().getImageClarity())
                    .average().orElse(0));
                summary.setAverageLightingQuality(analyses.stream()
                    .mapToDouble(a -> a.getQualityMetrics().getLightingQuality())
                    .average().orElse(0));
                summary.setAverageAngleQuality(analyses.stream()
                    .mapToDouble(a -> a.getQualityMetrics().getAngleQuality())
                    .average().orElse(0));
                summary.setAverageBackgroundQuality(analyses.stream()
                    .mapToDouble(a -> a.getQualityMetrics().getBackgroundQuality())
                    .average().orElse(0));
            }
            
            summary.setOverallQualityScore(analysisResult.getOverallQualityScore());
            
            // Image type distribution
            summary.setImageTypeDistribution(analyses.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    a -> a.getImageType().toString(),
                    java.util.stream.Collectors.counting())));
            
            // Quality recommendations
            List<String> qualityRecommendations = analysisResult.getRecommendations().stream()
                .filter(r -> r.contains("photo") || r.contains("image") || r.contains("lighting"))
                .toList();
            summary.setQualityRecommendations(qualityRecommendations);
            
            summary.setLastAnalyzed(analysisResult.getAnalysisTimestamp());
            
            return ResponseEntity.ok(ApiResponse.success(summary, 
                    "Image quality analysis completed successfully"));

        } catch (Exception e) {
            log.error("Error analyzing image quality for vehicle {}: {}", vehicleId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to analyze image quality: " + e.getMessage()));
        }
    }

    /**
     * Get damage assessment report
     */
    @GetMapping("/damage-assessment/{vehicleId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get damage assessment", 
               description = "Get AI-powered damage detection and assessment report")
    public ResponseEntity<ApiResponse<DamageAssessmentSummary>> getDamageAssessment(
            @Parameter(description = "Vehicle ID") @PathVariable Long vehicleId) {
        
        try {
            log.info("Generating damage assessment for vehicle: {}", vehicleId);
            
            ImageAnalysisResult analysisResult = imageRecognitionService.getCachedAnalysis(vehicleId);
            
            DamageAssessmentSummary summary = new DamageAssessmentSummary();
            summary.setVehicleId(vehicleId);
            
            // Analyze damage levels across images
            List<ImageAnalysis> analyses = analysisResult.getImageAnalyses();
            long noDamageCount = analyses.stream().mapToLong(a -> 
                a.getConditionAssessment().getDamageLevel() == DamageLevel.NONE ? 1 : 0).sum();
            long minorDamageCount = analyses.stream().mapToLong(a -> 
                a.getConditionAssessment().getDamageLevel() == DamageLevel.MINOR ? 1 : 0).sum();
            long moderateDamageCount = analyses.stream().mapToLong(a -> 
                a.getConditionAssessment().getDamageLevel() == DamageLevel.MODERATE ? 1 : 0).sum();
            long majorDamageCount = analyses.stream().mapToLong(a -> 
                a.getConditionAssessment().getDamageLevel() == DamageLevel.MAJOR ? 1 : 0).sum();
            
            summary.setNoDamageImages((int) noDamageCount);
            summary.setMinorDamageImages((int) minorDamageCount);
            summary.setModerateDamageImages((int) moderateDamageCount);
            summary.setMajorDamageImages((int) majorDamageCount);
            
            // Overall damage assessment
            if (majorDamageCount > 0) {
                summary.setOverallDamageLevel(DamageLevel.MAJOR);
                summary.setDamageImpact("Significant damage detected - may affect vehicle value and safety");
            } else if (moderateDamageCount > 0) {
                summary.setOverallDamageLevel(DamageLevel.MODERATE);
                summary.setDamageImpact("Moderate damage detected - should be addressed before sale");
            } else if (minorDamageCount > 0) {
                summary.setOverallDamageLevel(DamageLevel.MINOR);
                summary.setDamageImpact("Minor wear detected - typical for vehicle age");
            } else {
                summary.setOverallDamageLevel(DamageLevel.NONE);
                summary.setDamageImpact("No significant damage detected - excellent condition");
            }
            
            // Collect wear indicators
            summary.setWearIndicators(analyses.stream()
                .flatMap(a -> a.getConditionAssessment().getWearIndicators().stream())
                .distinct()
                .toList());
            
            summary.setConditionScore(analysisResult.getConditionScore());
            summary.setLastAnalyzed(analysisResult.getAnalysisTimestamp());
            
            return ResponseEntity.ok(ApiResponse.success(summary, 
                    "Damage assessment completed successfully"));

        } catch (Exception e) {
            log.error("Error generating damage assessment for vehicle {}: {}", vehicleId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to generate damage assessment: " + e.getMessage()));
        }
    }

    /**
     * Image recognition service health check
     */
    @GetMapping("/service-health")
    @Operation(summary = "Service health check", 
               description = "Check the health and status of image recognition services")
    public ResponseEntity<ApiResponse<ServiceHealthInfo>> getServiceHealth() {
        
        try {
            ServiceHealthInfo health = new ServiceHealthInfo();
            health.setStatus("healthy");
            health.setTimestamp(System.currentTimeMillis());
            health.setServices(List.of(
                "image-analysis",
                "feature-detection",
                "condition-assessment",
                "damage-detection",
                "quality-analysis"
            ));
            health.setFeatures(List.of(
                "ai-powered-analysis",
                "multi-image-processing",
                "feature-recognition",
                "condition-scoring",
                "damage-assessment",
                "quality-metrics",
                "batch-processing",
                "caching-support"
            ));
            health.setVersion("1.0.0");
            health.setSupportedFormats(List.of("JPEG", "PNG", "BMP", "TIFF", "WEBP"));
            health.setMaxImageSize("10MB");
            health.setMaxImagesPerAnalysis(20);
            
            return ResponseEntity.ok(ApiResponse.success(health, 
                    "Image recognition services are healthy"));

        } catch (Exception e) {
            log.error("Image recognition service health check failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Service health check failed: " + e.getMessage()));
        }
    }

    // Helper methods for feature categorization
    
    private boolean isExteriorFeature(String featureName) {
        return List.of("LED_HEADLIGHTS", "SUNROOF", "SPOILER", "ROOF_RAILS", "ALLOY_WHEELS",
                      "FOG_LIGHTS", "TINTED_WINDOWS", "SIDE_STEPS", "BULL_BAR", "CHROME_TRIM",
                      "LED_TAILLIGHTS", "PARKING_SENSORS").contains(featureName);
    }
    
    private boolean isInteriorFeature(String featureName) {
        return List.of("LEATHER_SEATS", "HEATED_SEATS", "NAVIGATION_SYSTEM", "BACKUP_CAMERA",
                      "PREMIUM_SOUND_SYSTEM", "DUAL_ZONE_CLIMATE", "POWER_SEATS", "WOODEN_TRIM",
                      "AMBIENT_LIGHTING", "PANORAMIC_ROOF", "DIGITAL_CLUSTER", "TOUCHSCREEN_INFOTAINMENT",
                      "HEADS_UP_DISPLAY").contains(featureName);
    }
    
    private boolean isSafetyFeature(String featureName) {
        return List.of("ABS", "AIRBAGS", "STABILITY_CONTROL", "BLIND_SPOT_MONITORING",
                      "LANE_DEPARTURE_WARNING", "COLLISION_AVOIDANCE", "PARKING_SENSORS",
                      "ADAPTIVE_CRUISE_CONTROL", "EMERGENCY_BRAKING", "TIRE_PRESSURE_MONITORING").contains(featureName);
    }
    
    private boolean isTechnicalFeature(String featureName) {
        return List.of("TURBO_ENGINE", "PERFORMANCE_AIR_FILTER", "ENGINE_COVER", "PERFORMANCE_TIRES",
                      "SPORT_BRAKES").contains(featureName);
    }

    // Data classes for API responses

    public static class VehicleConditionSummary {
        private Long vehicleId;
        private double overallConditionScore;
        private double qualityScore;
        private double marketabilityScore;
        private double featureScore;
        private double exteriorScore;
        private double interiorScore;
        private double technicalScore;
        private List<String> riskFactors;
        private List<String> valueIndicators;
        private double authenticityScore;
        private List<String> recommendations;
        private java.time.LocalDateTime lastAnalyzed;

        // Getters and setters
        public Long getVehicleId() { return vehicleId; }
        public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
        
        public double getOverallConditionScore() { return overallConditionScore; }
        public void setOverallConditionScore(double overallConditionScore) { this.overallConditionScore = overallConditionScore; }
        
        public double getQualityScore() { return qualityScore; }
        public void setQualityScore(double qualityScore) { this.qualityScore = qualityScore; }
        
        public double getMarketabilityScore() { return marketabilityScore; }
        public void setMarketabilityScore(double marketabilityScore) { this.marketabilityScore = marketabilityScore; }
        
        public double getFeatureScore() { return featureScore; }
        public void setFeatureScore(double featureScore) { this.featureScore = featureScore; }
        
        public double getExteriorScore() { return exteriorScore; }
        public void setExteriorScore(double exteriorScore) { this.exteriorScore = exteriorScore; }
        
        public double getInteriorScore() { return interiorScore; }
        public void setInteriorScore(double interiorScore) { this.interiorScore = interiorScore; }
        
        public double getTechnicalScore() { return technicalScore; }
        public void setTechnicalScore(double technicalScore) { this.technicalScore = technicalScore; }
        
        public List<String> getRiskFactors() { return riskFactors; }
        public void setRiskFactors(List<String> riskFactors) { this.riskFactors = riskFactors; }
        
        public List<String> getValueIndicators() { return valueIndicators; }
        public void setValueIndicators(List<String> valueIndicators) { this.valueIndicators = valueIndicators; }
        
        public double getAuthenticityScore() { return authenticityScore; }
        public void setAuthenticityScore(double authenticityScore) { this.authenticityScore = authenticityScore; }
        
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
        
        public java.time.LocalDateTime getLastAnalyzed() { return lastAnalyzed; }
        public void setLastAnalyzed(java.time.LocalDateTime lastAnalyzed) { this.lastAnalyzed = lastAnalyzed; }
    }

    public static class VehicleFeaturesSummary {
        private Long vehicleId;
        private List<DetectedFeatureSummary> detectedFeatures;
        private int totalFeaturesDetected;
        private List<DetectedFeatureSummary> exteriorFeatures;
        private List<DetectedFeatureSummary> interiorFeatures;
        private List<DetectedFeatureSummary> safetyFeatures;
        private List<DetectedFeatureSummary> technicalFeatures;
        private java.time.LocalDateTime lastAnalyzed;

        // Getters and setters
        public Long getVehicleId() { return vehicleId; }
        public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
        
        public List<DetectedFeatureSummary> getDetectedFeatures() { return detectedFeatures; }
        public void setDetectedFeatures(List<DetectedFeatureSummary> detectedFeatures) { this.detectedFeatures = detectedFeatures; }
        
        public int getTotalFeaturesDetected() { return totalFeaturesDetected; }
        public void setTotalFeaturesDetected(int totalFeaturesDetected) { this.totalFeaturesDetected = totalFeaturesDetected; }
        
        public List<DetectedFeatureSummary> getExteriorFeatures() { return exteriorFeatures; }
        public void setExteriorFeatures(List<DetectedFeatureSummary> exteriorFeatures) { this.exteriorFeatures = exteriorFeatures; }
        
        public List<DetectedFeatureSummary> getInteriorFeatures() { return interiorFeatures; }
        public void setInteriorFeatures(List<DetectedFeatureSummary> interiorFeatures) { this.interiorFeatures = interiorFeatures; }
        
        public List<DetectedFeatureSummary> getSafetyFeatures() { return safetyFeatures; }
        public void setSafetyFeatures(List<DetectedFeatureSummary> safetyFeatures) { this.safetyFeatures = safetyFeatures; }
        
        public List<DetectedFeatureSummary> getTechnicalFeatures() { return technicalFeatures; }
        public void setTechnicalFeatures(List<DetectedFeatureSummary> technicalFeatures) { this.technicalFeatures = technicalFeatures; }
        
        public java.time.LocalDateTime getLastAnalyzed() { return lastAnalyzed; }
        public void setLastAnalyzed(java.time.LocalDateTime lastAnalyzed) { this.lastAnalyzed = lastAnalyzed; }
    }

    public static class DetectedFeatureSummary {
        private String featureName;
        private double confidenceScore;
        private String description;

        // Getters and setters
        public String getFeatureName() { return featureName; }
        public void setFeatureName(String featureName) { this.featureName = featureName; }
        
        public double getConfidenceScore() { return confidenceScore; }
        public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class ImageQualitySummary {
        private Long vehicleId;
        private int totalImages;
        private double averageClarity;
        private double averageLightingQuality;
        private double averageAngleQuality;
        private double averageBackgroundQuality;
        private double overallQualityScore;
        private java.util.Map<String, Long> imageTypeDistribution;
        private List<String> qualityRecommendations;
        private java.time.LocalDateTime lastAnalyzed;

        // Getters and setters
        public Long getVehicleId() { return vehicleId; }
        public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
        
        public int getTotalImages() { return totalImages; }
        public void setTotalImages(int totalImages) { this.totalImages = totalImages; }
        
        public double getAverageClarity() { return averageClarity; }
        public void setAverageClarity(double averageClarity) { this.averageClarity = averageClarity; }
        
        public double getAverageLightingQuality() { return averageLightingQuality; }
        public void setAverageLightingQuality(double averageLightingQuality) { this.averageLightingQuality = averageLightingQuality; }
        
        public double getAverageAngleQuality() { return averageAngleQuality; }
        public void setAverageAngleQuality(double averageAngleQuality) { this.averageAngleQuality = averageAngleQuality; }
        
        public double getAverageBackgroundQuality() { return averageBackgroundQuality; }
        public void setAverageBackgroundQuality(double averageBackgroundQuality) { this.averageBackgroundQuality = averageBackgroundQuality; }
        
        public double getOverallQualityScore() { return overallQualityScore; }
        public void setOverallQualityScore(double overallQualityScore) { this.overallQualityScore = overallQualityScore; }
        
        public java.util.Map<String, Long> getImageTypeDistribution() { return imageTypeDistribution; }
        public void setImageTypeDistribution(java.util.Map<String, Long> imageTypeDistribution) { this.imageTypeDistribution = imageTypeDistribution; }
        
        public List<String> getQualityRecommendations() { return qualityRecommendations; }
        public void setQualityRecommendations(List<String> qualityRecommendations) { this.qualityRecommendations = qualityRecommendations; }
        
        public java.time.LocalDateTime getLastAnalyzed() { return lastAnalyzed; }
        public void setLastAnalyzed(java.time.LocalDateTime lastAnalyzed) { this.lastAnalyzed = lastAnalyzed; }
    }

    public static class DamageAssessmentSummary {
        private Long vehicleId;
        private int noDamageImages;
        private int minorDamageImages;
        private int moderateDamageImages;
        private int majorDamageImages;
        private DamageLevel overallDamageLevel;
        private String damageImpact;
        private List<String> wearIndicators;
        private double conditionScore;
        private java.time.LocalDateTime lastAnalyzed;

        // Getters and setters
        public Long getVehicleId() { return vehicleId; }
        public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
        
        public int getNoDamageImages() { return noDamageImages; }
        public void setNoDamageImages(int noDamageImages) { this.noDamageImages = noDamageImages; }
        
        public int getMinorDamageImages() { return minorDamageImages; }
        public void setMinorDamageImages(int minorDamageImages) { this.minorDamageImages = minorDamageImages; }
        
        public int getModerateDamageImages() { return moderateDamageImages; }
        public void setModerateDamageImages(int moderateDamageImages) { this.moderateDamageImages = moderateDamageImages; }
        
        public int getMajorDamageImages() { return majorDamageImages; }
        public void setMajorDamageImages(int majorDamageImages) { this.majorDamageImages = majorDamageImages; }
        
        public DamageLevel getOverallDamageLevel() { return overallDamageLevel; }
        public void setOverallDamageLevel(DamageLevel overallDamageLevel) { this.overallDamageLevel = overallDamageLevel; }
        
        public String getDamageImpact() { return damageImpact; }
        public void setDamageImpact(String damageImpact) { this.damageImpact = damageImpact; }
        
        public List<String> getWearIndicators() { return wearIndicators; }
        public void setWearIndicators(List<String> wearIndicators) { this.wearIndicators = wearIndicators; }
        
        public double getConditionScore() { return conditionScore; }
        public void setConditionScore(double conditionScore) { this.conditionScore = conditionScore; }
        
        public java.time.LocalDateTime getLastAnalyzed() { return lastAnalyzed; }
        public void setLastAnalyzed(java.time.LocalDateTime lastAnalyzed) { this.lastAnalyzed = lastAnalyzed; }
    }

    public static class ServiceHealthInfo {
        private String status;
        private long timestamp;
        private List<String> services;
        private List<String> features;
        private String version;
        private List<String> supportedFormats;
        private String maxImageSize;
        private int maxImagesPerAnalysis;

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
        
        public List<String> getSupportedFormats() { return supportedFormats; }
        public void setSupportedFormats(List<String> supportedFormats) { this.supportedFormats = supportedFormats; }
        
        public String getMaxImageSize() { return maxImageSize; }
        public void setMaxImageSize(String maxImageSize) { this.maxImageSize = maxImageSize; }
        
        public int getMaxImagesPerAnalysis() { return maxImagesPerAnalysis; }
        public void setMaxImagesPerAnalysis(int maxImagesPerAnalysis) { this.maxImagesPerAnalysis = maxImagesPerAnalysis; }
    }
}
