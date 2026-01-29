package com.carselling.oldcar.service.analytics;

import com.carselling.oldcar.dto.vehicle.VehicleRecommendationDto;
import com.carselling.oldcar.dto.vehicle.VehicleSummaryDto;
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

    private static final int DEFAULT_RECOMMENDATION_LIMIT = 5;

    /**
     * Get cars similar to a specific car (Content-Based)
     */
    @Transactional(readOnly = true)
    public List<VehicleRecommendationDto> getSimilarCars(Long carId) {
        log.debug("Generating similar car recommendations for car ID: {}", carId);

        return carRepository.findById(carId)
                .map(sourceCar -> {
                    BigDecimal price = sourceCar.getPrice();
                    BigDecimal minPrice = price.multiply(BigDecimal.valueOf(0.8)); // -20%
                    BigDecimal maxPrice = price.multiply(BigDecimal.valueOf(1.2)); // +20%

                    List<Car> similarCars = carRepository.findSimilarCars(
                            sourceCar.getMake(),
                            sourceCar.getModel(),
                            minPrice,
                            maxPrice,
                            sourceCar.getId(),
                            PageRequest.of(0, DEFAULT_RECOMMENDATION_LIMIT));

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
     * Get personalized recommendations for a user
     */
    @Transactional(readOnly = true)
    public List<VehicleRecommendationDto> getPersonalizedRecommendations(Long userId) {
        log.debug("Generating personalized recommendations for user ID: {}", userId);

        // 1. Get Last Viewed Car (Cold Start Mitigation)
        Pageable pageable = PageRequest.of(0, 1);
        Page<UserAnalyticsEvent> events = analyticsRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        if (events.hasContent()) {
            UserAnalyticsEvent lastEvent = events.getContent().get(0);
            if (UserAnalyticsEvent.TargetType.CAR.equals(lastEvent.getTargetType())
                    && lastEvent.getTargetId() != null) {
                try {
                    Long lastViewedCarId = Long.parseLong(lastEvent.getTargetId());
                    List<VehicleRecommendationDto> similar = getSimilarCars(lastViewedCarId);
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
                .viewCount(car.getViewCount() != null ? car.getViewCount().intValue() : 0)
                .build();
    }
}
