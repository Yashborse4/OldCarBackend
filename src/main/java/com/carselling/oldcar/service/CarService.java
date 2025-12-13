package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.car.*;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.exception.UnauthorizedActionException;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced Car Service V2 with analytics and advanced features
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CarService {

    private final CarRepository carRepository;
    private final UserRepository userRepository;

    /**
     * Get all vehicles with pagination
     */
    @Transactional(readOnly = true)
    public Page<CarResponseV2> getAllVehicles(Pageable pageable) {
        log.debug("Getting all vehicles with pagination: {}", pageable);
        
        Page<Car> cars = carRepository.findAll(pageable);
        List<CarResponseV2> carResponses = cars.getContent().stream()
                .map(this::convertToResponseV2)
                .collect(Collectors.toList());
        
        return new PageImpl<>(carResponses, pageable, cars.getTotalElements());
    }

    /**
     * Get vehicle by ID
     */
    @Transactional(readOnly = true)
    public CarResponseV2 getVehicleById(String id) {
        log.debug("Getting vehicle by ID: {}", id);
        
        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));
        
        // Increment view count
        car.setViewCount(car.getViewCount() != null ? (long) car.getViewCount() + 1L : 1L);
        carRepository.save(car);
        
        return convertToResponseV2(car);
    }

    /**
     * Create a new vehicle
     */
    public CarResponseV2 createVehicle(CarRequest request, Long currentUserId) {
        log.debug("Creating new vehicle for user: {}", currentUserId);
        
        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId.toString()));

        Car car = Car.builder()
                .make(request.getMake())
                .model(request.getModel())
                .year(request.getYear())
                .price(request.getPrice())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .mileage(request.getMileage())
                .fuelType(request.getFuelType())
                .transmission(request.getTransmission())
                .color(request.getColor())
                .vin(request.getVin())
                .numberOfOwners(request.getNumberOfOwners())
                .isActive(true)
                .isFeatured(false)
                .isSold(false)
                .viewCount(0L)
                .owner(owner)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Car savedCar = carRepository.save(car);
        log.info("Created new vehicle with ID: {} for user: {}", savedCar.getId(), currentUserId);
        
        return convertToResponseV2(savedCar);
    }

    /**
     * Update vehicle
     */
    public CarResponseV2 updateVehicle(String id, CarRequest request, Long currentUserId) {
        log.debug("Updating vehicle: {} by user: {}", id, currentUserId);
        
        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        // Check ownership or admin role
        if (!car.getOwner().getId().equals(currentUserId) && !isAdmin(currentUserId)) {
            throw new UnauthorizedActionException("You can only update your own vehicles");
        }

        // Update fields
        car.setMake(request.getMake());
        car.setModel(request.getModel());
        car.setYear(request.getYear());
        car.setPrice(request.getPrice());
        car.setDescription(request.getDescription());
        car.setImageUrl(request.getImageUrl());
        car.setMileage(request.getMileage());
        car.setFuelType(request.getFuelType());
        car.setTransmission(request.getTransmission());
        car.setColor(request.getColor());
        car.setVin(request.getVin());
        car.setNumberOfOwners(request.getNumberOfOwners());
        car.setUpdatedAt(LocalDateTime.now());

        Car updatedCar = carRepository.save(car);
        log.info("Updated vehicle with ID: {} by user: {}", id, currentUserId);
        
        return convertToResponseV2(updatedCar);
    }

    /**
     * Delete vehicle
     */
    public void deleteVehicle(String id, Long currentUserId, boolean hard) {
        log.debug("Deleting vehicle: {} by user: {} (hard: {})", id, currentUserId, hard);
        
        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        // Check ownership or admin role
        if (!car.getOwner().getId().equals(currentUserId) && !isAdmin(currentUserId)) {
            throw new UnauthorizedActionException("You can only delete your own vehicles");
        }

        if (hard) {
            carRepository.delete(car);
            log.info("Hard deleted vehicle with ID: {} by user: {}", id, currentUserId);
        } else {
            car.setIsActive(false);
            car.setUpdatedAt(LocalDateTime.now());
            carRepository.save(car);
            log.info("Soft deleted vehicle with ID: {} by user: {}", id, currentUserId);
        }
    }

    /**
     * Update vehicle status
     */
    public CarResponseV2 updateVehicleStatus(String id, String status, Long currentUserId) {
        log.debug("Updating vehicle status: {} to {} by user: {}", id, status, currentUserId);
        
        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        // Check ownership or admin role
        if (!car.getOwner().getId().equals(currentUserId) && !isAdmin(currentUserId)) {
            throw new UnauthorizedActionException("You can only update your own vehicles");
        }

        // Update status based on string value
        switch (status.toUpperCase()) {
            case "AVAILABLE":
                car.setIsActive(true);
                car.setIsSold(false);
                break;
            case "SOLD":
                car.setIsSold(true);
                break;
            case "INACTIVE":
                car.setIsActive(false);
                break;
            default:
                throw new IllegalArgumentException("Invalid status: " + status);
        }

        car.setUpdatedAt(LocalDateTime.now());
        Car updatedCar = carRepository.save(car);
        
        return convertToResponseV2(updatedCar);
    }

    /**
     * Search vehicles with filters
     */
    @Transactional(readOnly = true)
    public Page<CarResponseV2> searchVehicles(CarSearchCriteria criteria, Pageable pageable) {
        log.debug("Searching vehicles with criteria: {}", criteria);
        
        // For now, return all vehicles (can be enhanced with actual search implementation)
        Page<Car> cars = carRepository.findAll(pageable);
        List<CarResponseV2> carResponses = cars.getContent().stream()
                .map(this::convertToResponseV2)
                .collect(Collectors.toList());
        
        return new PageImpl<>(carResponses, pageable, cars.getTotalElements());
    }

    /**
     * Get vehicle analytics (mock implementation)
     */
    @Transactional(readOnly = true)
    public CarAnalyticsResponse getVehicleAnalytics(String id, Long currentUserId) {
        log.debug("Getting analytics for vehicle: {} by user: {}", id, currentUserId);
        
        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        // Check ownership or admin role
        if (!car.getOwner().getId().equals(currentUserId) && !isAdmin(currentUserId)) {
            throw new UnauthorizedActionException("You can only view analytics for your own vehicles");
        }

        // Mock analytics data
        return CarAnalyticsResponse.builder()
                .vehicleId(id)
                .views((long) (car.getViewCount() != null ? car.getViewCount() : 0))
                .inquiries((long) (Math.random() * 50))
                .shares((long) (Math.random() * 25))
                .coListings(0L)
                .avgTimeOnMarket(30)
                .lastActivity(LocalDateTime.now())
                .topLocations(List.of("Mumbai", "Delhi", "Bangalore"))
                .dealerInterest((int) (Math.random() * 100))
                .build();
    }

    /**
     * Toggle featured status
     */
    public CarResponseV2 toggleFeatureVehicle(String id, boolean featured, Long currentUserId) {
        log.debug("Toggling feature status for vehicle: {} to {} by user: {}", id, featured, currentUserId);
        
        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        // Check ownership or admin/dealer role
        if (!car.getOwner().getId().equals(currentUserId) && !isDealerOrHigher(currentUserId)) {
            throw new UnauthorizedActionException("You don't have permission to feature vehicles");
        }

        car.setIsFeatured(featured);
        car.setFeaturedUntil(featured ? LocalDateTime.now().plusDays(30) : null);
        car.setUpdatedAt(LocalDateTime.now());

        Car updatedCar = carRepository.save(car);
        return convertToResponseV2(updatedCar);
    }

    /**
     * Track vehicle view
     */
    public void trackVehicleView(String id) {
        log.debug("Tracking view for vehicle: {}", id);
        
        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        car.setViewCount(car.getViewCount() != null ? (long) car.getViewCount() + 1L : 1L);
        carRepository.save(car);
    }

    /**
     * Track vehicle share
     */
    public void trackVehicleShare(String id, String platform) {
        log.debug("Tracking share for vehicle: {} on platform: {}", id, platform);
        
        // Can be enhanced with actual tracking implementation
        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));
        
        // For now, just increment view count as a proxy
        car.setViewCount(car.getViewCount() != null ? (long) car.getViewCount() + 1L : 1L);
        carRepository.save(car);
    }

    /**
     * Get similar vehicles (mock implementation)
     */
    @Transactional(readOnly = true)
    public List<CarResponseV2> getSimilarVehicles(String id, int limit) {
        log.debug("Getting similar vehicles for: {} with limit: {}", id, limit);
        
        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        // Simple implementation: get cars of the same make
        List<Car> similarCars = carRepository.findByMakeAndIdNot(car.getMake(), car.getId())
                .stream()
                .limit(limit)
                .collect(Collectors.toList());

        return similarCars.stream()
                .map(this::convertToResponseV2)
                .collect(Collectors.toList());
    }

    /**
     * Get vehicles by dealer
     */
    @Transactional(readOnly = true)
    public Page<CarResponseV2> getVehiclesByDealer(String dealerId, String status, Pageable pageable) {
        log.debug("Getting vehicles by dealer: {} with status: {}", dealerId, status);
        
        Page<Car> cars = carRepository.findByOwnerId(Long.parseLong(dealerId), pageable);
        List<CarResponseV2> carResponses = cars.getContent().stream()
                .map(this::convertToResponseV2)
                .collect(Collectors.toList());
        
        return new PageImpl<>(carResponses, pageable, cars.getTotalElements());
    }

    // Helper methods

    private CarResponseV2 convertToResponseV2(Car car) {
        return CarResponseV2.builder()
                .id(car.getId().toString())
                .make(car.getMake())
                .model(car.getModel())
                .year(car.getYear())
                .price(car.getPrice() != null ? car.getPrice().longValue() : 0L)
                .mileage(car.getMileage() != null ? car.getMileage().longValue() : 0L)
                .location(car.getOwner().getLocation())
                .condition("Good") // Default value
                .images(List.of(car.getImageUrl() != null ? car.getImageUrl() : ""))
                .specifications(CarResponseV2.CarSpecifications.builder()
                        .fuelType(car.getFuelType())
                        .transmission(car.getTransmission())
                        .color(car.getColor())
                        .build())
                .dealerId(car.getOwner().getId().toString())
                .dealerName(car.getOwner().getUsername())
                .isCoListed(false)
                .coListedIn(List.of())
                .views(car.getViewCount() != null ? (long) car.getViewCount() : 0L)
                .inquiries(0L)
                .shares(0L)
                .status(getCarStatus(car))
                .featured(Boolean.TRUE.equals(car.getIsFeatured()) && 
                         (car.getFeaturedUntil() == null || car.getFeaturedUntil().isAfter(LocalDateTime.now())))
                .createdAt(car.getCreatedAt())
                .updatedAt(car.getUpdatedAt())
                .build();
    }

    private String getCarStatus(Car car) {
        if (Boolean.TRUE.equals(car.getIsSold())) {
            return "Sold";
        } else if (Boolean.TRUE.equals(car.getIsActive())) {
            return "Available";
        } else {
            return "Inactive";
        }
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(user -> "ADMIN".equals(user.getRole().name()))
                .orElse(false);
    }

    private boolean isDealerOrHigher(Long userId) {
        return userRepository.findById(userId)
                .map(user -> {
                    String role = user.getRole().name();
                    return "ADMIN".equals(role) || "DEALER".equals(role);
                })
                .orElse(false);
    }

    /**
     * Get car statistics for admin dashboard
     */
    @Transactional(readOnly = true)
    public CarStatistics getCarStatistics() {
        log.debug("Getting car statistics for admin dashboard");
        
        long totalCars = carRepository.count();
        long activeCars = carRepository.countByIsActive(true);
        long soldCars = carRepository.countByIsSold(true);
        long featuredCars = carRepository.countByIsFeaturedTrueAndFeaturedUntilAfter(LocalDateTime.now());
        
        // Calculate new cars in last 7 days
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long newCarsLast7Days = carRepository.countCarsCreatedSince(sevenDaysAgo);
        
        return CarStatistics.builder()
                .totalCars(totalCars)
                .activeCars(activeCars)
                .soldCars(soldCars)
                .featuredCars(featuredCars)
                .inactiveCars(totalCars - activeCars)
                .newCarsLast7Days(newCarsLast7Days)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    // Inner class for statistics
    @lombok.Data
    @lombok.Builder
    public static class CarStatistics {
        private long totalCars;
        private long activeCars;
        private long soldCars;
        private long inactiveCars;
        private long featuredCars;
        private long newCarsLast7Days;
        private LocalDateTime lastUpdated;
        
        // Helper method to get new cars in last 7 days (placeholder)
        public long getNewCarsLast7Days() {
            return newCarsLast7Days;
        }
    }
}
