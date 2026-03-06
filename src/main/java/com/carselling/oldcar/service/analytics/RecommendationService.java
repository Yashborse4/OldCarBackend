package com.carselling.oldcar.service.analytics;

import com.carselling.oldcar.dto.user.UserPreferenceDto;
import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.dto.vehicle.VehicleRecommendationDto;
import com.carselling.oldcar.dto.vehicle.VehicleSummaryDto;
import java.util.ArrayList;
import java.util.List;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.UserAnalyticsEvent;
import com.carselling.oldcar.model.UserPreferenceScore;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserAnalyticsEventRepository;
import com.carselling.oldcar.repository.UserPreferenceScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
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
    private final UserPreferenceScoreRepository scoreRepository;

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
     * Get personalized recommendations using the Behavioral Scoring Formula
     * 40% Behavior, 25% Location, 20% Recency, 10% Popularity, 5% Price
     */
    @Transactional(readOnly = true)
    public List<VehicleRecommendationDto> getPersonalizedRecommendations(Long userId, String city) {
        log.debug("Generating behavioral recommendations for user ID: {}", userId);

        List<UserPreferenceScore> userScores = scoreRepository.findByUserIdOrderByScoreDesc(userId);

        // Cold start fallback
        if (userScores.isEmpty()) {
            return getColdStartRecommendations(userId, city);
        }

        // Candidate pool (Recent / Popular cars for performance)
        List<Car> candidatePool = carRepository.findTrendingCars(PageRequest.of(0, 200));

        return candidatePool.stream()
                .map(car -> scoreCandidate(car, userScores, city))
                .sorted((a, b) -> Double.compare(b.getRecommendationScore(), a.getRecommendationScore()))
                .limit(20)
                .collect(Collectors.toList());
    }

    private VehicleRecommendationDto scoreCandidate(Car car, List<UserPreferenceScore> userScores, String userCity) {
        double maxBehaviorScore = userScores.stream().mapToDouble(UserPreferenceScore::getScore).max().orElse(1.0);

        // 1. Behavioral Match (40%)
        double behaviorMatch = 0.0;
        for (UserPreferenceScore rs : userScores) {
            boolean match = false;
            switch (rs.getAttributeType()) {
                case MAKE -> match = rs.getAttributeValue().equalsIgnoreCase(car.getMake());
                case CATEGORY -> match = rs.getAttributeValue().equalsIgnoreCase(car.getCategory());
                case FUEL_TYPE -> match = rs.getAttributeValue().equalsIgnoreCase(car.getFuelType());
                case BODY_TYPE -> match = rs.getAttributeValue().equalsIgnoreCase(car.getVariant());
                case PRICE_BUCKET ->
                    match = rs.getAttributeValue().equalsIgnoreCase(determinePriceBucket(car.getPrice()));
            }
            if (match) {
                behaviorMatch += (rs.getScore() / maxBehaviorScore);
            }
        }
        behaviorMatch = Math.min(1.0, behaviorMatch);

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
                "Recommended based on your recent activity");
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

        // 1. Get Last Viewed Car (Cold Start Mitigation)
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

        // 2. Fallback: Trending / Most Viewed (Popularity Based)
        List<Car> trendingCars = carRepository.findTrendingCars(PageRequest.of(0, DEFAULT_RECOMMENDATION_LIMIT));
        return trendingCars.stream()
                .map(car -> new VehicleRecommendationDto(
                        convertToSummary(car),
                        0.8, // Static score for trending
                        "Trending on our platform"))
                .collect(Collectors.toList());
    }

    /**
     * Guest recommendations based on explicit static preferences
     */
    public List<VehicleRecommendationDto> getGuestRecommendations(UserPreferenceDto guestPrefs, String city) {
        log.debug("Generating guest recommendations");
        List<Car> candidatePool = carRepository.findTrendingCars(PageRequest.of(0, 200));

        // Create temporary "mock" UserPreferenceScores based on DTO to reuse the
        // scoring logic
        List<UserPreferenceScore> mockScores = new ArrayList<>();

        if (guestPrefs != null) {
            if (guestPrefs.getVehicleTypes() != null) {
                guestPrefs.getVehicleTypes()
                        .forEach(type -> mockScores.add(
                                UserPreferenceScore.builder().attributeType(UserPreferenceScore.AttributeType.CATEGORY)
                                        .attributeValue(type).score(5.0).build()));
            }
            if (guestPrefs.getBudgetRanges() != null) {
                guestPrefs.getBudgetRanges()
                        .forEach(budget -> mockScores.add(UserPreferenceScore.builder()
                                .attributeType(UserPreferenceScore.AttributeType.PRICE_BUCKET).attributeValue(budget)
                                .score(5.0).build()));
            }
        }

        if (mockScores.isEmpty()) {
            // Pure generic trending if guest sends nothing
            return getColdStartRecommendations(null, city);
        }

        return candidatePool.stream()
                .map(car -> scoreCandidate(car, mockScores, city))
                .sorted((a, b) -> Double.compare(b.getRecommendationScore(), a.getRecommendationScore()))
                .limit(20)
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
