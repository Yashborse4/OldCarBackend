package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.car.CarRequest;
import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.exception.InsufficientPermissionException;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Car Service for car management operations
 * Handles car CRUD operations, search, filtering, and car-related business logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CarService {

    private final CarRepository carRepository;
    private final AuthService authService;
    private final UserService userService;

    /**
     * Get all active cars (public endpoint)
     */
    @Transactional(readOnly = true)
    public Page<CarResponse> getAllActiveCars(Pageable pageable) {
        log.info("Retrieving all active cars");
        
        Page<Car> cars = carRepository.findAllActiveCars(pageable);
        return cars.map(this::convertToCarResponse);
    }

    /**
     * Get car by ID (public endpoint)
     */
    @Transactional(readOnly = true)
    public CarResponse getCarById(Long carId) {
        log.info("Retrieving car by ID: {}", carId);

        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", carId));

        // Increment view count for active cars
        if (Boolean.TRUE.equals(car.getIsActive())) {
            car.incrementViewCount();
            carRepository.save(car);
        }

        return convertToCarResponse(car);
    }

    /**
     * Search cars with filters (public endpoint)
     */
    @Transactional(readOnly = true)
    public Page<CarResponse> searchCars(String make, String model, Integer minYear, Integer maxYear,
                                       BigDecimal minPrice, BigDecimal maxPrice, Role ownerRole,
                                       Boolean isFeatured, Boolean isSold, Pageable pageable) {
        log.info("Searching cars with filters");

        Page<Car> cars = carRepository.findCarsByCriteria(
                make, model, minYear, maxYear, minPrice, maxPrice, 
                ownerRole, isFeatured, isSold, pageable);

        return cars.map(this::convertToCarResponse);
    }

    /**
     * Full text search across cars (public endpoint)
     */
    @Transactional(readOnly = true)
    public Page<CarResponse> searchCarsByText(String searchTerm, Pageable pageable) {
        log.info("Full text search for cars with term: {}", searchTerm);

        Page<Car> cars = carRepository.searchCars(searchTerm, pageable);
        return cars.map(this::convertToCarResponse);
    }

    /**
     * Get featured cars (public endpoint)
     */
    @Transactional(readOnly = true)
    public Page<CarResponse> getFeaturedCars(Pageable pageable) {
        log.info("Retrieving featured cars");

        Page<Car> cars = carRepository.findCurrentlyFeaturedCars(pageable);
        return cars.map(this::convertToCarResponse);
    }

    /**
     * Get recent cars (public endpoint)
     */
    @Transactional(readOnly = true)
    public Page<CarResponse> getRecentCars(Pageable pageable) {
        log.info("Retrieving recent cars");

        Page<Car> cars = carRepository.findRecentCars(pageable);
        return cars.map(this::convertToCarResponse);
    }

    /**
     * Get most viewed cars (public endpoint)
     */
    @Transactional(readOnly = true)
    public Page<CarResponse> getMostViewedCars(Pageable pageable) {
        log.info("Retrieving most viewed cars");

        Page<Car> cars = carRepository.findMostViewedCars(pageable);
        return cars.map(this::convertToCarResponse);
    }

    /**
     * Create a new car (requires SELLER/DEALER role)
     */
    public CarResponse createCar(CarRequest request) {
        log.info("Creating new car");

        User currentUser = authService.getCurrentUser();
        
        // Check if user has permission to create cars
        if (!currentUser.hasPermission("car:create")) {
            throw new InsufficientPermissionException("You don't have permission to create cars");
        }

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
                .owner(currentUser)
                .isActive(true)
                .isFeatured(false)
                .isSold(false)
                .viewCount(0L)
                .build();

        try {
            car = carRepository.save(car);
            log.info("Car created successfully with ID: {} by user: {}", car.getId(), currentUser.getUsername());
            return convertToCarResponse(car);
        } catch (Exception e) {
            log.error("Error creating car for user: {}", currentUser.getUsername(), e);
            throw new RuntimeException("Failed to create car", e);
        }
    }

    /**
     * Update car (owner or admin only)
     */
    public CarResponse updateCar(Long carId, CarRequest request) {
        log.info("Updating car with ID: {}", carId);

        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", carId));

        User currentUser = authService.getCurrentUser();

        // Check if user can update this car
        if (!car.canBeEditedBy(currentUser)) {
            throw new InsufficientPermissionException("You don't have permission to update this car");
        }

        // Update car fields
        boolean updated = false;

        if (StringUtils.hasText(request.getMake()) && !request.getMake().equals(car.getMake())) {
            car.setMake(request.getMake());
            updated = true;
        }

        if (StringUtils.hasText(request.getModel()) && !request.getModel().equals(car.getModel())) {
            car.setModel(request.getModel());
            updated = true;
        }

        if (request.getYear() != null && !request.getYear().equals(car.getYear())) {
            car.setYear(request.getYear());
            updated = true;
        }

        if (request.getPrice() != null && !request.getPrice().equals(car.getPrice())) {
            car.setPrice(request.getPrice());
            updated = true;
        }

        if (StringUtils.hasText(request.getDescription()) && !request.getDescription().equals(car.getDescription())) {
            car.setDescription(request.getDescription());
            updated = true;
        }

        if (StringUtils.hasText(request.getImageUrl()) && !request.getImageUrl().equals(car.getImageUrl())) {
            car.setImageUrl(request.getImageUrl());
            updated = true;
        }

        if (request.getMileage() != null && !request.getMileage().equals(car.getMileage())) {
            car.setMileage(request.getMileage());
            updated = true;
        }

        if (StringUtils.hasText(request.getFuelType()) && !request.getFuelType().equals(car.getFuelType())) {
            car.setFuelType(request.getFuelType());
            updated = true;
        }

        if (StringUtils.hasText(request.getTransmission()) && !request.getTransmission().equals(car.getTransmission())) {
            car.setTransmission(request.getTransmission());
            updated = true;
        }

        if (StringUtils.hasText(request.getColor()) && !request.getColor().equals(car.getColor())) {
            car.setColor(request.getColor());
            updated = true;
        }

        if (StringUtils.hasText(request.getVin()) && !request.getVin().equals(car.getVin())) {
            car.setVin(request.getVin());
            updated = true;
        }

        if (request.getNumberOfOwners() != null && !request.getNumberOfOwners().equals(car.getNumberOfOwners())) {
            car.setNumberOfOwners(request.getNumberOfOwners());
            updated = true;
        }

        if (updated) {
            car = carRepository.save(car);
            log.info("Car updated successfully with ID: {} by user: {}", car.getId(), currentUser.getUsername());
        } else {
            log.info("No changes detected for car ID: {}", car.getId());
        }

        return convertToCarResponse(car);
    }

    /**
     * Delete car (owner or admin only)
     */
    public void deleteCar(Long carId, boolean hardDelete) {
        log.info("Deleting car with ID: {}, hardDelete: {}", carId, hardDelete);

        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", carId));

        User currentUser = authService.getCurrentUser();

        // Check if user can delete this car
        if (!car.canBeDeletedBy(currentUser)) {
            throw new InsufficientPermissionException("You don't have permission to delete this car");
        }

        try {
            if (hardDelete && currentUser.hasRole(Role.ADMIN)) {
                // Hard delete (admin only)
                carRepository.delete(car);
                log.info("Car hard deleted successfully with ID: {} by admin: {}", carId, currentUser.getUsername());
            } else {
                // Soft delete (set isActive to false)
                car.setIsActive(false);
                carRepository.save(car);
                log.info("Car soft deleted successfully with ID: {} by user: {}", carId, currentUser.getUsername());
            }
        } catch (Exception e) {
            log.error("Error deleting car with ID: {} by user: {}", carId, currentUser.getUsername(), e);
            throw new RuntimeException("Failed to delete car", e);
        }
    }

    /**
     * Feature/Unfeature car (DEALER/ADMIN only)
     */
    public CarResponse toggleCarFeature(Long carId, boolean featured, int daysToFeature) {
        log.info("Toggling feature status for car ID: {}, featured: {}", carId, featured);

        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", carId));

        User currentUser = authService.getCurrentUser();

        // Check if user can feature cars
        if (!car.canBeFeaturedBy(currentUser)) {
            throw new InsufficientPermissionException("You don't have permission to feature cars");
        }

        car.setFeatured(featured, daysToFeature);
        car = carRepository.save(car);

        log.info("Car feature status updated successfully for car ID: {} by user: {}", 
                carId, currentUser.getUsername());

        return convertToCarResponse(car);
    }

    /**
     * Mark car as sold/unsold
     */
    public CarResponse markCarAsSold(Long carId, boolean sold) {
        log.info("Marking car ID: {} as sold: {}", carId, sold);

        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", carId));

        User currentUser = authService.getCurrentUser();

        // Check if user can update this car
        if (!car.canBeEditedBy(currentUser)) {
            throw new InsufficientPermissionException("You don't have permission to update this car");
        }

        car.setIsSold(sold);
        car = carRepository.save(car);

        log.info("Car sold status updated successfully for car ID: {} by user: {}", 
                carId, currentUser.getUsername());

        return convertToCarResponse(car);
    }

    /**
     * Get user's own cars (SELLER/DEALER only)
     */
    @Transactional(readOnly = true)
    public Page<CarResponse> getUserCars(Pageable pageable) {
        log.info("Retrieving current user's cars");

        User currentUser = authService.getCurrentUser();

        // Check if user has permission to view cars
        if (!currentUser.hasPermission("car:create")) {
            throw new InsufficientPermissionException("You don't have permission to view your cars");
        }

        Page<Car> cars = carRepository.findActiveCarsByOwner(currentUser, pageable);
        return cars.map(this::convertToCarResponse);
    }

    /**
     * Get cars by owner ID (admin only)
     */
    @Transactional(readOnly = true)
    public Page<CarResponse> getCarsByOwnerId(Long ownerId, Pageable pageable) {
        log.info("Retrieving cars for owner ID: {}", ownerId);

        User currentUser = authService.getCurrentUser();
        
        // Check if user is admin
        if (!currentUser.hasRole(Role.ADMIN)) {
            throw new InsufficientPermissionException("You don't have permission to view cars by owner");
        }

        Page<Car> cars = carRepository.findActiveCarsByOwnerId(ownerId, pageable);
        return cars.map(this::convertToCarResponse);
    }

    /**
     * Get car statistics (admin only)
     */
    @Transactional(readOnly = true)
    public CarStatistics getCarStatistics() {
        log.info("Getting car statistics");

        User currentUser = authService.getCurrentUser();
        
        // Check if user is admin
        if (!currentUser.hasRole(Role.ADMIN)) {
            throw new InsufficientPermissionException("You don't have permission to view car statistics");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);
        LocalDateTime sevenDaysAgo = now.minusDays(7);

        return CarStatistics.builder()
                .totalCars(carRepository.count())
                .activeCars(carRepository.countByIsActive(true))
                .inactiveCars(carRepository.countByIsActive(false))
                .featuredCars(carRepository.countFeaturedCars())
                .soldCars(carRepository.countByIsSold(true))
                .availableCars(carRepository.countByIsSold(false))
                .newCarsLast30Days(carRepository.countCarsCreatedSince(thirtyDaysAgo))
                .newCarsLast7Days(carRepository.countCarsCreatedSince(sevenDaysAgo))
                .averagePrice(carRepository.getAverageCarPrice())
                .build();
    }

    // Private helper methods

    private CarResponse convertToCarResponse(Car car) {
        return CarResponse.builder()
                .id(car.getId())
                .make(car.getMake())
                .model(car.getModel())
                .year(car.getYear())
                .price(car.getPrice())
                .description(car.getDescription())
                .imageUrl(car.getImageUrl())
                .owner(userService.convertToUserSummary(car.getOwner()))
                .isActive(car.getIsActive())
                .isFeatured(car.getIsFeatured())
                .isSold(car.getIsSold())
                .viewCount(car.getViewCount())
                .mileage(car.getMileage())
                .fuelType(car.getFuelType())
                .transmission(car.getTransmission())
                .color(car.getColor())
                .vin(car.getVin())
                .numberOfOwners(car.getNumberOfOwners())
                .featuredUntil(car.getFeaturedUntil())
                .createdAt(car.getCreatedAt())
                .updatedAt(car.getUpdatedAt())
                .build();
    }

    // Inner class for car statistics
    @lombok.Data
    @lombok.Builder
    public static class CarStatistics {
        private long totalCars;
        private long activeCars;
        private long inactiveCars;
        private long featuredCars;
        private long soldCars;
        private long availableCars;
        private long newCarsLast30Days;
        private long newCarsLast7Days;
        private BigDecimal averagePrice;
    }
}
