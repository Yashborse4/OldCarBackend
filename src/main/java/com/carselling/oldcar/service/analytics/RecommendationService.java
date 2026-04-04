package com.carselling.oldcar.service.analytics;

import com.carselling.oldcar.dto.user.UserPreferenceDto;
import com.carselling.oldcar.dto.vehicle.VehicleRecommendationDto;
import com.carselling.oldcar.dto.vehicle.VehicleSummaryDto;
import java.util.List;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.UserAnalyticsEvent;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserAnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Service for generating smart vehicle recommendations
 * Uses Hybrid Approach: Content-Based + Lite Collaborative Filtering
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final CarRepository carRepository;
    private final UserAnalyticsEventRepository analyticsRepository;

    private static final int DEFAULT_RECOMMENDATION_LIMIT = 5;

    /**
     * Get cars similar to a specific car (Content-Based)
     */
    @Transactional(readOnly = true)
    public List<VehicleRecommendationDto> getSimilarCars(Long carId, String city) {
        log.debug("Generating similar car recommendations for car ID: {}", carId);

        return carRepository.findById(carId)
                .map(sourceCar -> {
                    BigDecimal price = sourceCar.getPrice();
                    BigDecimal minPrice = price.multiply(BigDecimal.valueOf(0.8)); // -20%
                    BigDecimal maxPrice = price.multiply(BigDecimal.valueOf(1.2)); // +20%

                    List<Car> similarCars;

                    if (city != null && !city.isEmpty()) {
                        similarCars = carRepository.findSimilarCarsInCity(
                                sourceCar.getMake(),
                                sourceCar.getModel(),
                                sourceCar.getYear() - 5,
                                sourceCar.getYear() + 5,
                                minPrice,
                                maxPrice,
                                sourceCar.getId(),
                                city,
                                PageRequest.of(0, DEFAULT_RECOMMENDATION_LIMIT));
                    } else {
                        similarCars = carRepository.findSimilarCars(
                                sourceCar.getMake(),
                                sourceCar.getModel(),
                                minPrice,
                                maxPrice,
                                sourceCar.getId(),
                                PageRequest.of(0, DEFAULT_RECOMMENDATION_LIMIT));
                    }

                    return similarCars.stream()
                            .map(car -> new VehicleRecommendationDto(
                                    convertToSummary(car),
                                    calculateSimilarityScore(sourceCar, car),
                                    "Similar to " + sourceCar.getMake() + " " + sourceCar.getModel()))
                            .collect(Collectors.toList());
                })
                .orElse(Collections.emptyList());
    }

    /**
     * Recommendations based on explicit frontend preferences
     */
    @Transactional(readOnly = true)
    public List<VehicleRecommendationDto> getRecommendationsBasedOnPreferences(UserPreferenceDto prefs, String city) {
        log.debug("Generating recommendations based on frontend preferences");

        if (prefs == null || ((prefs.getVehicleTypes() == null || prefs.getVehicleTypes().isEmpty()) && 
                              (prefs.getBudgetRanges() == null || prefs.getBudgetRanges().isEmpty()))) {
            // Pure generic trending if guest sends nothing
            return getColdStartRecommendations(null, city);
        }

        List<Car> candidatePool = carRepository.findTrendingCars(PageRequest.of(0, 200));

        return candidatePool.stream()
                .map(car -> scoreCandidate(car, prefs, city))
                .sorted((a, b) -> Double.compare(b.getRecommendationScore(), a.getRecommendationScore()))
                .limit(20)
                .collect(Collectors.toList());
    }

    private VehicleRecommendationDto scoreCandidate(Car car, UserPreferenceDto prefs, String userCity) {
        // 1. Behavioral Match (40%)
        double behaviorMatch = 0.0;
        int preferenceCount = 0;
        
        if (prefs.getVehicleTypes() != null && !prefs.getVehicleTypes().isEmpty()) {
            preferenceCount++;
            for (String type : prefs.getVehicleTypes()) {
                if (type.equalsIgnoreCase(car.getCategory()) || type.equalsIgnoreCase(car.getVariant())) {
                    behaviorMatch += 1.0;
                    break;
                }
            }
        }
        
        if (prefs.getBudgetRanges() != null && !prefs.getBudgetRanges().isEmpty()) {
            preferenceCount++;
            String carBucket = determinePriceBucket(car.getPrice());
            for (String bucket : prefs.getBudgetRanges()) {
                if (bucket.equalsIgnoreCase(carBucket)) {
                    behaviorMatch += 1.0;
                    break;
                }
            }
        }
        
        // Normalize behavior match to max 1.0
        if (preferenceCount > 0) {
            behaviorMatch = Math.min(1.0, behaviorMatch / preferenceCount);
        }

        // 2. Location Score (25%)
        double locationScore = 0.0;
        if (userCity != null && userCity.equalsIgnoreCase(car.getLocation())) {
            locationScore = 1.0;
        }

        // 3. Recency Score (20%)
        double recencyScore = 0.0;
        if (car.getCreatedAt() != null) {
            long daysOld = java.time.temporal.ChronoUnit.DAYS.between(car.getCreatedAt().toLocalDate(),
                    java.time.LocalDate.now());
            if (daysOld == 0)
                recencyScore = 1.0;
            else if (daysOld <= 7)
                recencyScore = 0.5;
            else
                recencyScore = 0.1;
        }

        // 4. Popularity Score (10%)
        double popularityScore = 0.0;
        long eng = car.getViewCount() + (car.getInquiryCount() != null ? car.getInquiryCount() * 10 : 0);
        popularityScore = Math.min(1.0, eng / 100.0);

        // 5. Price Competitiveness (5%) - Assume average
        double priceScore = 0.5;

        // Final Calculation
        double finalScore = (0.40 * behaviorMatch) +
                (0.25 * locationScore) +
                (0.20 * recencyScore) +
                (0.10 * popularityScore) +
                (0.05 * priceScore);

        return new VehicleRecommendationDto(
                convertToSummary(car),
                Math.round(finalScore * 100.0) / 100.0,
                "Recommended based on your preferences");
    }

    private String determinePriceBucket(BigDecimal price) {
        if (price == null)
            return "UNKNOWN";
        double p = price.doubleValue();
        if (p < 200000)
            return "BELOW_2L";
        if (p < 500000)
            return "2L_5L";
        if (p < 1000000)
            return "5L_10L";
        if (p < 2000000)
            return "10L_20L";
        return "ABOVE_20L";
    }

    /**
     * Cold Start recommendations for new users
     */
    private List<VehicleRecommendationDto> getColdStartRecommendations(Long userId, String city) {
        log.debug("Generating cold start recommendations for user ID: {}", userId);

        // 1. Get Last Viewed Car (Cold Start Mitigation) if userId is somewhat present
        if (userId != null) {
            Pageable pageable = PageRequest.of(0, 1);
            Page<UserAnalyticsEvent> events = analyticsRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

            if (events.hasContent()) {
                UserAnalyticsEvent lastEvent = events.getContent().get(0);
                if (UserAnalyticsEvent.TargetType.CAR.equals(lastEvent.getTargetType())
                        && lastEvent.getTargetId() != null) {
                    try {
                        Long lastViewedCarId = Long.parseLong(lastEvent.getTargetId());
                        List<VehicleRecommendationDto> similar = getSimilarCars(lastViewedCarId, city);
                        if (!similar.isEmpty()) {
                            // Enrich reason
                            similar.forEach(r -> r.setReason("Because you viewed similar cars"));
                            return similar;
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Invalid targetId in analytics for recommendation: {}", lastEvent.getTargetId());
                    }
                }
            }
        }

        // 2. Fallback: Trending / Most Viewed (Popularity Based)
        List<Car> trendingCars = carRepository.findTrendingCars(PageRequest.of(0, DEFAULT_RECOMMENDATION_LIMIT));
        return trendingCars.stream()
                .map(car -> new VehicleRecommendationDto(
                        convertToSummary(car),
                        0.8, // Static score for trending
                        "Trending on our platform"))
                .collect(Collectors.toList());
    }

    private Double calculateSimilarityScore(Car source, Car target) {
        double score = 1.0;
        if (source.getMake().equalsIgnoreCase(target.getMake()))
            score += 0.5;
        if (source.getModel().equalsIgnoreCase(target.getModel()))
            score += 0.3;
        if (source.getYear().equals(target.getYear()))
            score += 0.1;
        // Simple heuristic
        return Math.min(score, 2.0); // Cap at 2.0
    }

    private VehicleSummaryDto convertToSummary(Car car) {
        return VehicleSummaryDto.builder()
                .id(car.getId())
                .make(car.getMake())
                .model(car.getModel())
                .year(car.getYear())
                .price(car.getPrice())
                .mileage(car.getMileage())
                .primaryImage(car.getImageUrl())
                .location(car.getLocation())
                .isAvailable(car.getIsAvailable())
                .isFeatured(car.getIsFeatured())
                .viewCount(Math.toIntExact(car.getViewCount()))
                .status(car.getStatus() != null ? car.getStatus().name() : null)
                .build();
    }
}
