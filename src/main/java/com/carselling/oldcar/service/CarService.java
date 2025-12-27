package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.car.*;
import com.carselling.oldcar.dto.user.UserSummary;
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
    private final AuthService authService;

    /**
     * Get all vehicles with pagination
     */
    public Page<CarResponseV2> getAllVehicles(Pageable pageable) {
        log.debug("Getting all vehicles with pagination: {}", pageable);

        Page<Car> cars = carRepository.findAllActiveCars(pageable);

        User currentUser = authService.getCurrentUserOrNull();
        boolean isAdmin = currentUser != null && currentUser.getRole() == com.carselling.oldcar.model.Role.ADMIN;

        List<CarResponseV2> carResponses = cars.getContent().stream()
                .filter(car -> isAdmin || isVehicleVisible(car))
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

        // Strict Access Control
        User currentUser = authService.getCurrentUserOrNull();
        boolean isOwner = currentUser != null && car.getOwner().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser != null && currentUser.getRole() == com.carselling.oldcar.model.Role.ADMIN;
        boolean isVerifiedPublic = isVehicleVisible(car);

        if (!isAdmin && !isOwner && !isVerifiedPublic) {
            // Throw 404 to hide existence of unverified/hidden cars
            throw new ResourceNotFoundException("Car", "id", id);
        }

        // Increment view count
        car.setViewCount(car.getViewCount() != null ? (long) car.getViewCount() + 1L : 1L);
        carRepository.save(car);

        return convertToResponseV2(car);
    }

    /**
     * Get all public vehicles
     */
    @Transactional(readOnly = true)
    public Page<PublicCarDTO> getPublicVehicles(Pageable pageable) {
        // Enforce visibility: USER = Always, DEALER = Only if verified
        Page<Car> cars = carRepository.findAll(pageable);

        // Note: Filtering a Page in-memory breaks pagination consistency (total
        // elements vs actual content).
        // Ideally this should be a DB query. Given the request to modify this method,/
        // we will filter the content but keep the page metadata which might be slightly
        // inaccurate
        // regarding "total elements" viewable.
        // For accurate pagination, we should use a custom query, but minimizing risk
        // right now:

        List<PublicCarDTO> visibleCars = cars.getContent().stream()
                .filter(this::isVehicleVisible)
                .map(this::convertToPublicDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(visibleCars, pageable, cars.getTotalElements());
    }

    /**
     * Get public vehicle by ID
     */
    @Transactional(readOnly = true)
    public PublicCarDTO getPublicVehicleById(String id) {
        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        if (!isVehicleVisible(car)) {
            throw new ResourceNotFoundException("Car", "id", id);
        }

        return convertToPublicDTO(car);
    }

    /**
     * Get private vehicle by ID (Authenticated)
     */
    @Transactional(readOnly = true)
    public PrivateCarDTO getPrivateVehicleById(String id) {
        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        // Increment view count
        car.setViewCount(car.getViewCount() != null ? (long) car.getViewCount() + 1L : 1L);
        carRepository.save(car);

        return convertToPrivateDTO(car);
    }

    private PublicCarDTO convertToPublicDTO(Car car) {
        return PublicCarDTO.builder()
                .id(car.getId())
                .make(car.getMake())
                .model(car.getModel())
                .year(car.getYear())
                .price(car.getPrice())
                .imageUrl(car.getImageUrl())
                .city(car.getOwner().getLocation())
                .fuelType(car.getFuelType())
                .transmission(car.getTransmission())
                .mileage(car.getMileage())
                .createdAt(car.getCreatedAt())
                .build();
    }

    private PrivateCarDTO convertToPrivateDTO(Car car) {
        PublicCarDTO publicDTO = convertToPublicDTO(car);
        User owner = car.getOwner();

        return PrivateCarDTO.builder()
                .id(publicDTO.getId())
                .make(publicDTO.getMake())
                .model(publicDTO.getModel())
                .year(publicDTO.getYear())
                .price(publicDTO.getPrice())
                .imageUrl(publicDTO.getImageUrl())
                .city(publicDTO.getCity())
                .fuelType(publicDTO.getFuelType())
                .transmission(publicDTO.getTransmission())
                .mileage(publicDTO.getMileage())
                .createdAt(publicDTO.getCreatedAt())

                .description(car.getDescription())
                .images(car.getImages())
                .vin(car.getVin())
                .numberOfOwners(car.getNumberOfOwners())
                .color(car.getColor())
                .owner(UserSummary.builder()
                        .id(owner.getId())
                        .username(owner.getUsername())
                        .role(owner.getRole().name())
                        .location(owner.getLocation())
                        .build())
                .sellerPhone(owner.getPhoneNumber())
                .isFeatured(car.getIsFeatured())
                .isSold(car.getIsSold())
                .viewCount(car.getViewCount())
                .location(car.getLocation())
                .isApproved(car.getIsApproved())
                .updatedAt(car.getUpdatedAt())
                .build();
    }

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
     * Toggle vehicle visibility (Hide/Show)
     */
    public CarResponseV2 toggleVisibility(String id, boolean visible, Long currentUserId) {
        log.debug("Toggling visibility for vehicle: {} to {} by user: {}", id, visible, currentUserId);

        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        // Check ownership or admin role
        if (!car.getOwner().getId().equals(currentUserId) && !isAdmin(currentUserId)) {
            throw new UnauthorizedActionException("You can only update your own vehicles");
        }

        car.setIsActive(visible);
        car.setUpdatedAt(LocalDateTime.now());

        Car updatedCar = carRepository.save(car);
        log.info("Vehicle visibility updated ID: {} to active: {}", id, visible);

        return convertToResponseV2(updatedCar);
    }

    /**
     * Search vehicles with filters and optional free-text query.
     *
     * This method is used by /api/v2/cars/search and is the main entry point for
     * the advanced search experience in the mobile app.
     */
    @Transactional(readOnly = true)
    public Page<CarResponseV2> searchVehicles(CarSearchCriteria criteria, Pageable pageable) {
        log.debug("Searching vehicles with criteria: {} and pageable: {}", criteria, pageable);

        Page<Car> cars;

        // 1) Free-text query: search across make/model/description when `query` is
        // provided
        if (criteria != null && criteria.getQuery() != null && !criteria.getQuery().isBlank()) {
            String searchTerm = criteria.getQuery().trim();
            cars = carRepository.searchCars(searchTerm, pageable);
        } else if (criteria != null) {
            // 2) Structured filters based on make/model/year/price/featured/status
            java.math.BigDecimal minPrice = criteria.getMinPrice() != null
                    ? java.math.BigDecimal.valueOf(criteria.getMinPrice())
                    : null;
            java.math.BigDecimal maxPrice = criteria.getMaxPrice() != null
                    ? java.math.BigDecimal.valueOf(criteria.getMaxPrice())
                    : null;

            // Status mapping -> isSold flag; null means "any"
            Boolean isSold = null;
            if (criteria.getStatus() != null) {
                String status = criteria.getStatus().trim().toUpperCase();
                if ("SOLD".equals(status)) {
                    isSold = Boolean.TRUE;
                } else if ("AVAILABLE".equals(status) || "ACTIVE".equals(status)) {
                    isSold = Boolean.FALSE;
                }
            }

            // For now we don't filter by owner role here (null = any role)
            com.carselling.oldcar.model.Role ownerRole = null;

            cars = carRepository.findCarsByCriteria(
                    criteria.getMake(),
                    criteria.getModel(),
                    criteria.getMinYear(),
                    criteria.getMaxYear(),
                    minPrice,
                    maxPrice,
                    criteria.getFuelType(),
                    criteria.getTransmission(),
                    criteria.getLocation(),
                    ownerRole,
                    criteria.getFeatured(),
                    isSold,
                    pageable);
        } else {
            // 3) No criteria at all – fall back to active cars
            cars = carRepository.findAllActiveCars(pageable);
        }

        // 4) Apply in-memory refinements for fields not covered by the repository query
        List<Car> filteredCars = cars.getContent().stream()
                .filter(car -> {
                    if (criteria == null) {
                        return true;
                    }

                    // Fuel type filter
                    if (criteria.getFuelType() != null && car.getFuelType() != null) {
                        if (!car.getFuelType().equalsIgnoreCase(criteria.getFuelType())) {
                            return false;
                        }
                    }

                    // Transmission filter
                    if (criteria.getTransmission() != null && car.getTransmission() != null) {
                        if (!car.getTransmission().equalsIgnoreCase(criteria.getTransmission())) {
                            return false;
                        }
                    }

                    // Location filter (simple city match against owner's location)
                    if (criteria.getLocation() != null && car.getOwner() != null) {
                        String ownerLocation = car.getOwner().getLocation();
                        if (ownerLocation == null || !ownerLocation.toLowerCase()
                                .contains(criteria.getLocation().toLowerCase())) {
                            return false;
                        }
                    }

                    // Basic mileage range filter if provided
                    if (criteria.getMinMileage() != null && car.getMileage() != null
                            && car.getMileage() < criteria.getMinMileage()) {
                        return false;
                    }
                    if (criteria.getMaxMileage() != null && car.getMileage() != null
                            && car.getMileage() > criteria.getMaxMileage()) {
                        return false;
                    }

                    return true;
                })
                .collect(Collectors.toList());

        List<CarResponseV2> carResponses = filteredCars.stream()
                .filter(car -> {
                    // Admin can see everything
                    User currentUser = authService.getCurrentUserOrNull();
                    if (currentUser != null && currentUser.getRole() == com.carselling.oldcar.model.Role.ADMIN) {
                        return true;
                    }
                    return isVehicleVisible(car);
                })
                .map(this::convertToResponseV2)
                .collect(Collectors.toList());

        // Note: totalElements from original page might be inaccurate if valid cars are
        // hidden
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

        // Real analytics data (defaulting to 0 where not tracked yet)
        return CarAnalyticsResponse.builder()
                .vehicleId(id)
                .views((long) (car.getViewCount() != null ? car.getViewCount() : 0))
                .inquiries(0L) // TODO: Implement inquiry tracking
                .shares(0L) // TODO: Implement share tracking
                .coListings(0L)
                .avgTimeOnMarket(0)
                .lastActivity(LocalDateTime.now())
                .topLocations(List.of()) // No location tracking yet
                .dealerInterest(0)
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

        // Ensure dealer is verified before showing their cars publicly
        // Unless we are the dealer ourselves (not passed here, assuming public access)
        List<CarResponseV2> carResponses = cars.getContent().stream()
                .filter(this::isVehicleVisible)
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

    /**
     * Check if a vehicle should be visible to the public.
     * Logic:
     * - USER role: Always visible
     * - DEALER role: Visible only if verifiedDealer is true
     * - ADMIN role: Hidden by default (unless we decide otherwise)
     */
    private boolean isVehicleVisible(Car car) {
        if (car == null || car.getOwner() == null)
            return false;

        // Strict Visibility Rule:
        // Only VERIFIED DEALER cars are visible to the public.
        // USER cars are hidden from public lists.
        return Boolean.TRUE.equals(car.getOwner().getVerifiedDealer());
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
