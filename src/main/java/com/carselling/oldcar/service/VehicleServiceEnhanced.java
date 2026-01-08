package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.vehicle.*;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced Vehicle Service with advanced search, recommendations, and analytics
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class VehicleServiceEnhanced {

    private final CarRepository carRepository;
    private final UserRepository userRepository;

    /**
     * Advanced vehicle search with multiple filters
     */
    public Page<VehicleSearchResultDto> advancedSearch(VehicleSearchCriteria criteria, Pageable pageable) {
        Specification<Car> spec = createSearchSpecification(criteria);
        Page<Car> carPage = carRepository.findAll(spec, pageable);

        List<VehicleSearchResultDto> results = carPage.getContent().stream()
                .map(this::convertToSearchResultDto)
                .collect(Collectors.toList());

        return new PageImpl<>(results, pageable, carPage.getTotalElements());
    }

    /**
     * Get vehicle recommendations for a user
     */
    public List<VehicleRecommendationDto> getRecommendationsForUser(Long userId, int limit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Get user's search history and preferences (simplified algorithm)
        List<Car> recommendedCars = getRecommendedCars(user, limit);

        return recommendedCars.stream()
                .map(this::convertToRecommendationDto)
                .collect(Collectors.toList());
    }

    /**
     * Get similar vehicles based on a given vehicle
     */
    public List<VehicleSummaryDto> getSimilarVehicles(Long vehicleId, int limit) {
        Car vehicle = carRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        // Find similar cars based on make, model, year, price range
        List<Car> similarCars = carRepository.findSimilarCars(
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getYear() - 2, // 2 years before
                vehicle.getYear() + 2, // 2 years after
                vehicle.getPrice().multiply(BigDecimal.valueOf(0.8)), // 20% below
                vehicle.getPrice().multiply(BigDecimal.valueOf(1.2)), // 20% above
                vehicleId,
                pageable(limit));

        return similarCars.stream()
                .map(this::convertToSummaryDto)
                .collect(Collectors.toList());
    }

    /**
     * Get vehicle price history and analytics
     */
    public VehiclePriceAnalysisDto getPriceAnalysis(VehiclePriceAnalysisRequest request) {
        // Get price statistics for similar vehicles
        List<Object[]> priceStats = carRepository.getPriceStatistics(
                request.getMake(),
                request.getModel(),
                request.getYearFrom(),
                request.getYearTo(),
                request.getMileageFrom(),
                request.getMileageTo());

        if (priceStats.isEmpty()) {
            return VehiclePriceAnalysisDto.builder()
                    .averagePrice(BigDecimal.ZERO)
                    .minPrice(BigDecimal.ZERO)
                    .maxPrice(BigDecimal.ZERO)
                    .totalListings(0L)
                    .recommendation("No similar vehicles found for analysis")
                    .build();
        }

        Object[] stats = priceStats.get(0);
        BigDecimal avgPrice = (BigDecimal) stats[0];
        BigDecimal minPrice = (BigDecimal) stats[1];
        BigDecimal maxPrice = (BigDecimal) stats[2];
        Long count = (Long) stats[3];

        return VehiclePriceAnalysisDto.builder()
                .averagePrice(avgPrice)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .totalListings(count)
                .priceRange(maxPrice.subtract(minPrice))
                .recommendation(generatePriceRecommendation(request.getCurrentPrice(), avgPrice))
                .build();
    }

    /**
     * Add vehicle to user's favorites
     */
    public void addToFavorites(Long userId, Long vehicleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Car car = carRepository.findById(vehicleId)
                .orElseThrow(() -> new RuntimeException("Vehicle not found"));

        if (user.getFavoriteCars() == null) {
            user.setFavoriteCars(new HashSet<>());
        }

        user.getFavoriteCars().add(car);
        userRepository.save(user);

        log.info("User {} added vehicle {} to favorites", userId, vehicleId);
    }

    /**
     * Remove vehicle from user's favorites
     */
    public void removeFromFavorites(Long userId, Long vehicleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getFavoriteCars() != null) {
            user.getFavoriteCars().removeIf(car -> car.getId().equals(vehicleId));
            userRepository.save(user);
        }

        log.info("User {} removed vehicle {} from favorites", userId, vehicleId);
    }

    /**
     * Get user's favorite vehicles
     */
    public Page<VehicleSummaryDto> getUserFavorites(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getFavoriteCars() == null || user.getFavoriteCars().isEmpty()) {
            return Page.empty(pageable);
        }

        List<VehicleSummaryDto> favorites = user.getFavoriteCars().stream()
                .map(this::convertToSummaryDto)
                .collect(Collectors.toList());

        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), favorites.size());

        if (start >= favorites.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, favorites.size());
        }

        return new PageImpl<>(favorites.subList(start, end), pageable, favorites.size());
    }

    /**
     * Get trending vehicles (most viewed/favorited)
     */
    public List<VehicleTrendingDto> getTrendingVehicles(int limit) {
        List<Car> trendingCars = carRepository.findTrendingCars(pageable(limit));

        return trendingCars.stream()
                .map(this::convertToTrendingDto)
                .collect(Collectors.toList());
    }

    /**
     * Get vehicles by location radius
     */
    public Page<VehicleSearchResultDto> getVehiclesByLocation(
            Double latitude, Double longitude, Double radiusKm, Pageable pageable) {

        Page<Car> nearbyeCars = carRepository.findNearbyyCars(latitude, longitude, radiusKm, pageable);

        List<VehicleSearchResultDto> results = nearbyeCars.getContent().stream()
                .map(car -> {
                    VehicleSearchResultDto dto = convertToSearchResultDto(car);
                    // Calculate distance (simplified)
                    dto.setDistanceKm(calculateDistance(latitude, longitude,
                            car.getLatitude(), car.getLongitude()));
                    return dto;
                })
                .collect(Collectors.toList());

        return new PageImpl<>(results, pageable, nearbyeCars.getTotalElements());
    }

    /**
     * Track vehicle view (for analytics)
     */
    public void trackVehicleView(Long vehicleId, Long userId) {
        try {
            Car car = carRepository.findById(vehicleId).orElse(null);
            if (car != null) {
                car.incrementViewCount();
                car.setLastViewedAt(LocalDateTime.now());
                carRepository.save(car);

                // Log for analytics
                log.debug("Vehicle {} viewed by user {}", vehicleId, userId);
            }
        } catch (Exception e) {
            log.error("Error tracking vehicle view: {}", e.getMessage());
        }
    }

    /**
     * Track vehicle inquiry (for analytics)
     */
    public void trackVehicleInquiry(Long vehicleId, Long userId) {
        try {
            Car car = carRepository.findById(vehicleId).orElse(null);
            if (car != null) {
                car.incrementInquiryCount();
                carRepository.save(car);
                log.debug("Vehicle {} inquiry by user {}", vehicleId, userId);
            }
        } catch (Exception e) {
            log.error("Error tracking vehicle inquiry: {}", e.getMessage());
        }
    }

    /**
     * Track vehicle share (for analytics)
     */
    public void trackVehicleShare(Long vehicleId, Long userId) {
        try {
            Car car = carRepository.findById(vehicleId).orElse(null);
            if (car != null) {
                car.incrementShareCount();
                carRepository.save(car);
                log.debug("Vehicle {} shared by user {}", vehicleId, userId);
            }
        } catch (Exception e) {
            log.error("Error tracking vehicle share: {}", e.getMessage());
        }
    }

    // ========================= PRIVATE HELPER METHODS =========================

    /**
     * Create search specification from criteria
     */
    private Specification<Car> createSearchSpecification(VehicleSearchCriteria criteria) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Basic filters
            if (criteria.getMake() != null && !criteria.getMake().isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("make")),
                        "%" + criteria.getMake().toLowerCase() + "%"));
            }

            if (criteria.getModel() != null && !criteria.getModel().isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("model")),
                        "%" + criteria.getModel().toLowerCase() + "%"));
            }

            if (criteria.getYearFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("year"), criteria.getYearFrom()));
            }

            if (criteria.getYearTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("year"), criteria.getYearTo()));
            }

            if (criteria.getPriceFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("price"), criteria.getPriceFrom()));
            }

            if (criteria.getPriceTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("price"), criteria.getPriceTo()));
            }

            if (criteria.getMileageFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("mileage"), criteria.getMileageFrom()));
            }

            if (criteria.getMileageTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("mileage"), criteria.getMileageTo()));
            }

            if (criteria.getFuelType() != null && !criteria.getFuelType().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("fuelType"), criteria.getFuelType()));
            }

            if (criteria.getTransmission() != null && !criteria.getTransmission().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("transmission"), criteria.getTransmission()));
            }

            if (criteria.getColor() != null && !criteria.getColor().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("color"), criteria.getColor()));
            }

            // Only show available cars
            predicates.add(criteriaBuilder.isTrue(root.get("isAvailable")));
            predicates.add(criteriaBuilder.isTrue(root.get("isActive")));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Get recommended cars for user (simplified algorithm)
     */
    private List<Car> getRecommendedCars(User user, int limit) {
        // This is a simplified recommendation algorithm
        // In production, you might use more sophisticated ML algorithms

        return carRepository.findRecommendedCars(
                user.getId(),
                pageable(limit));
    }

    /**
     * Generate price recommendation
     */
    private String generatePriceRecommendation(BigDecimal currentPrice, BigDecimal averagePrice) {
        if (currentPrice == null || averagePrice == null) {
            return "Unable to provide price recommendation";
        }

        BigDecimal percentageDiff = currentPrice.subtract(averagePrice)
                .divide(averagePrice, 2, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (percentageDiff.compareTo(BigDecimal.valueOf(10)) > 0) {
            return "Price is " + percentageDiff.abs().intValue() + "% above market average. Consider lowering.";
        } else if (percentageDiff.compareTo(BigDecimal.valueOf(-10)) < 0) {
            return "Price is " + percentageDiff.abs().intValue() + "% below market average. Great deal!";
        } else {
            return "Price is within market range (" + percentageDiff.intValue() + "% of average).";
        }
    }

    /**
     * Calculate distance between two points (simplified)
     */
    private Double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return null;
        }

        // Simplified distance calculation (Haversine formula would be more accurate)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c; // Earth's radius in kilometers
    }

    // ========================= DTO CONVERSION METHODS =========================

    private VehicleSearchResultDto convertToSearchResultDto(Car car) {
        return VehicleSearchResultDto.builder()
                .id(car.getId())
                .make(car.getMake())
                .model(car.getModel())
                .year(car.getYear())
                .price(car.getPrice())
                .mileage(car.getMileage())
                .fuelType(car.getFuelType())
                .transmission(car.getTransmission())
                .color(car.getColor())
                .description(car.getDescription())
                .images(car.getImages())
                .location(car.getLocation())
                .sellerName(car.getOwner() != null ? car.getOwner().getFullName() : "Unknown")
                .viewCount(Math.toIntExact(car.getViewCount()))
                .isFeatured(car.getIsFeatured())
                .createdAt(car.getCreatedAt())
                .build();
    }

    private VehicleSummaryDto convertToSummaryDto(Car car) {
        return VehicleSummaryDto.builder()
                .id(car.getId())
                .make(car.getMake())
                .model(car.getModel())
                .year(car.getYear())
                .price(car.getPrice())
                .mileage(car.getMileage())
                .primaryImage(car.getImages() != null && !car.getImages().isEmpty() ? car.getImages().get(0) : null)
                .location(car.getLocation())
                .isAvailable(car.getIsAvailable())
                .isFeatured(car.getIsFeatured())
                .build();
    }

    private VehicleRecommendationDto convertToRecommendationDto(Car car) {
        return VehicleRecommendationDto.builder()
                .vehicle(convertToSummaryDto(car))
                .recommendationScore(85.0) // Placeholder score
                .reason("Based on your search history")
                .build();
    }

    private VehicleTrendingDto convertToTrendingDto(Car car) {
        return VehicleTrendingDto.builder()
                .vehicle(convertToSummaryDto(car))
                .viewCount(Math.toIntExact(car.getViewCount()))
                .trendingScore(car.getViewCount() * 1.2) // Simple trending calculation
                .build();
    }

    private Pageable pageable(int limit) {
        return org.springframework.data.domain.PageRequest.of(0, limit);
    }
}
