package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.dto.car.CarRequest;
import com.carselling.oldcar.dto.car.PublicCarDTO;
import com.carselling.oldcar.dto.car.PrivateCarDTO;
import com.carselling.oldcar.dto.car.CarSearchCriteria;
import com.carselling.oldcar.dto.car.CarAnalyticsResponse;

import com.carselling.oldcar.dto.CarStatistics;
import com.carselling.oldcar.dto.user.UserSummary;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.DealerStatus;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.MediaStatus;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.exception.UnauthorizedActionException;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.repository.CarMasterRepository;
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
import org.springframework.data.jpa.domain.Specification;

/**
 * Enhanced Car Service V2 with analytics and advanced features
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CarServiceImpl implements CarService {

    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final CarMasterRepository carMasterRepository;
    private final com.carselling.oldcar.b2.B2FileService b2FileService; // Injected instead of Firebase
    private final FileValidationService fileValidationService;

    /**
     * Get all vehicles with pagination
     */
    public Page<CarResponse> getAllVehicles(Pageable pageable) {
        log.debug("Getting all vehicles with pagination: {}", pageable);

        User currentUser = authService.getCurrentUserOrNull();
        boolean isAdmin = currentUser != null && currentUser.getRole() == com.carselling.oldcar.model.Role.ADMIN;

        Page<Car> cars;
        if (isAdmin) {
            cars = carRepository.findAllActiveCars(pageable);
        } else {
            cars = carRepository.findAllPublicCars(Role.USER, Role.DEALER, DealerStatus.VERIFIED, pageable);
        }

        return cars.map(this::convertToResponseV2);
    }

    /**
     * Get vehicle by ID
     */
    @Transactional(readOnly = true)
    public CarResponse getVehicleById(String id) {
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
        // Enforce visibility: USER = Always, DEALER = Only if verified using efficient
        // DB query
        Page<Car> cars = carRepository.findAllPublicCars(Role.USER, Role.DEALER, DealerStatus.VERIFIED, pageable);
        return cars.map(this::convertToPublicDTO);
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

    @Override
    public PublicCarDTO convertToPublicDTO(Car car) {
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

    @Override
    public PrivateCarDTO convertToPrivateDTO(Car car) {
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

    /**
     * Find car by idempotency key for duplicate prevention on retries.
     */
    @Transactional(readOnly = true)
    public CarResponse findByIdempotencyKey(String idempotencyKey, Long ownerId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return carRepository.findByIdempotencyKeyAndOwnerId(idempotencyKey, ownerId)
                .map(this::convertToResponseV2)
                .orElse(null);
    }

    /**
     * Create vehicle with optional idempotency key.
     */
    public CarResponse createVehicle(CarRequest request, Long currentUserId, String idempotencyKey) {
        log.debug("Creating new vehicle for user: {} (idempotency: {})", currentUserId, idempotencyKey);

        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId.toString()));

        Car car = Car.builder()
                .make(request.getMake())
                .model(request.getModel())
                .year(request.getYear())
                .price(request.getPrice())
                .description(request.getDescription())
                // Sanitize URL: if empty/blank, set to null to satisfy DB constraint
                .imageUrl(request.getImageUrl() != null && !request.getImageUrl().isBlank() ? request.getImageUrl()
                        : null)
                .videoUrl(request.getVideoUrl() != null && !request.getVideoUrl().isBlank() ? request.getVideoUrl()
                        : null)
                .mileage(request.getMileage())
                .fuelType(request.getFuelType())
                .transmission(request.getTransmission())
                .color(request.getColor())
                .numberOfOwners(request.getNumberOfOwners())
                .accidentHistory(request.getAccidentHistory())
                .repaintedParts(request.getRepaintedParts())
                .engineIssues(request.getEngineIssues())
                .floodDamage(request.getFloodDamage())
                .insuranceClaims(request.getInsuranceClaims())
                .variant(request.getVariant())
                .usage(request.getUsage())
                .variant(request.getVariant())
                .usage(request.getUsage())
                // Initialize as INACTIVE until media is ready
                .isActive(false)
                .isAvailable(false)
                .isFeatured(false)
                .isSold(false)
                .viewCount(0L)
                .owner(owner)
                // Ignore images in initial creation - they must be uploaded via upload API
                .images(new java.util.ArrayList<>())
                .mediaStatus(MediaStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Set CarMaster reference if carMasterId is provided
        if (request.getCarMasterId() != null) {
            carMasterRepository.findById(request.getCarMasterId())
                    .ifPresent(carMaster -> {
                        car.setCarMaster(carMaster);
                        log.debug("Linked car to CarMaster: {} {} ({})",
                                carMaster.getMake(), carMaster.getModel(), carMaster.getId());
                    });
        }

        // 1. If imageUrl (Banner) is NOT provided, but we have images, use the first
        // one as banner
        if ((car.getImageUrl() == null || car.getImageUrl().isBlank()) && !car.getImages().isEmpty()) {
            car.setImageUrl(car.getImages().get(0));
        }

        log.info("Saving new car. MediaStatus: {}, ImageUrl: '{}', VideoUrl: '{}'",
                car.getMediaStatus(), car.getImageUrl(), car.getVideoUrl());

        Car savedCar = carRepository.save(car);
        log.info("Created new vehicle with ID: {} for user: {}", savedCar.getId(), currentUserId);

        return convertToResponseV2(savedCar);
    }

    /**
     * Update vehicle
     */
    public CarResponse updateVehicle(String id, CarRequest request, Long currentUserId) {
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
        car.setMileage(request.getMileage());
        car.setFuelType(request.getFuelType());
        car.setTransmission(request.getTransmission());
        car.setColor(request.getColor());
        car.setNumberOfOwners(request.getNumberOfOwners());
        car.setAccidentHistory(request.getAccidentHistory());
        car.setRepaintedParts(request.getRepaintedParts());
        car.setEngineIssues(request.getEngineIssues());
        car.setFloodDamage(request.getFloodDamage());
        car.setInsuranceClaims(request.getInsuranceClaims());
        car.setVariant(request.getVariant());
        car.setUsage(request.getUsage());

        // Validate and set image URL - only allow trusted sources
        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            fileValidationService.validateFileUrl(request.getImageUrl());
            car.setImageUrl(request.getImageUrl());
        }

        // Validate and set images list
        if (request.getImages() != null) {
            for (String imageUrl : request.getImages()) {
                fileValidationService.validateFileUrl(imageUrl);
            }
            car.setImages(request.getImages());
        }

        // banner logic: if explicit imageUrl is null/blank, and we have images, set
        // first as banner
        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            car.setImageUrl(request.getImageUrl());
        } else if ((car.getImageUrl() == null || car.getImageUrl().isBlank()) && !car.getImages().isEmpty()) {
            car.setImageUrl(car.getImages().get(0));
        }

        car.setUpdatedAt(LocalDateTime.now());

        Car updatedCar = carRepository.save(car);
        log.info("Updated vehicle with ID: {} by user: {}", id, currentUserId);

        return convertToResponseV2(updatedCar);
    }

    /**
     * Delete vehicle
     * Performs a HARD DELETE after cleaning up associated media files.
     * Analytics are preserved via loose coupling (target_id).
     */
    public void deleteVehicle(String id, Long currentUserId, boolean hard) {
        log.debug("Deleting vehicle: {} by user: {} (force hard delete)", id, currentUserId);

        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        // Check ownership or admin role
        if (!car.getOwner().getId().equals(currentUserId) && !isAdmin(currentUserId)) {
            throw new UnauthorizedActionException("You can only delete your own vehicles");
        }

        // 1. Delete Media Files from Firebase
        try {
            // Delete images
            if (car.getImages() != null) {
                for (String imageUrl : car.getImages()) {
                    if (imageUrl != null && !imageUrl.isBlank()) {
                        try {
                            b2FileService.deleteFile(imageUrl);
                            log.debug("Deleted image: {}", imageUrl);
                        } catch (Exception e) {
                            log.warn("Failed to delete image: {} - {}", imageUrl, e.getMessage());
                        }
                    }
                }
            }

            // Delete video
            if (car.getVideoUrl() != null && !car.getVideoUrl().isBlank()) {
                try {
                    b2FileService.deleteFile(car.getVideoUrl());
                    log.debug("Deleted video: {}", car.getVideoUrl());
                } catch (Exception e) {
                    log.warn("Failed to delete video: {} - {}", car.getVideoUrl(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error during media cleanup for car {}: {}", id, e.getMessage());
            // Continue with DB delete even if media delete fails to avoid inconsistent
            // state
        }

        // 2. Perform Hard Delete
        carRepository.delete(car);
        log.info("Hard deleted vehicle with ID: {} by user: {}", id, currentUserId);
    }

    /**
     * Soft delete all cars for a specific user (Admin only internal use)
     */
    public void softDeleteUserCars(Long userId) {
        log.debug("Soft deleting all cars for user: {}", userId);
        carRepository.softDeleteCarsByOwner(userId);
    }

    /**
     * Update vehicle status
     */
    public CarResponse updateVehicleStatus(String id, String status, Long currentUserId) {
        log.debug("Updating vehicle status: {} to {} by user: {}", id, status, currentUserId);

        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        // Check ownership or admin role
        if (!car.getOwner().getId().equals(currentUserId) && !isAdmin(currentUserId)) {
            throw new UnauthorizedActionException("You can only update your own vehicles");
        }

        // Check dealer verification for public visibility
        if ("AVAILABLE".equalsIgnoreCase(status) && !car.getOwner().canListCarsPublicly()) {
            // Allow them to set it, but force isActive=false if they are not verified?
            // Or reject the request? The requirement is "dealer car visibility status will
            // be false".
            // Let's allow saving "AVAILABLE" intent but keep isActive=false if unverified.
            // Actually, to be safe and clear, let's allow the status update to 'AVAILABLE'
            // in the DB
            // but ensure isActive remains false effectively hiding it.
            // BUT, our visibility logic relies on isActive.

            // Re-reading logic in getAllVehicles: filter(car -> isAdmin ||
            // isVehicleVisible(car))
            // isVehicleVisible checks isActive.

            // So if they try to set "AVAILABLE", we should probably warn or set it but keep
            // hidden?
            // "dealer should be able to upload car... but no one see the car"
            // So we allow the operation, but override the effective visibility.
        }

        // Update status based on string value
        switch (status.toUpperCase()) {
            case "AVAILABLE":
                car.setIsSold(false);
                car.setIsAvailable(true);
                // Only activate if Verified
                if (car.getOwner().canListCarsPublicly()) {
                    car.setIsActive(true);
                } else {
                    car.setIsActive(false);
                    log.info("Dealer {} is not verified, keeping vehicle {} inactive despite AVAILABLE status",
                            currentUserId, id);
                }
                break;
            case "SOLD":
                car.setIsSold(true);
                car.setIsAvailable(false);
                break;
            case "RESERVED":
                car.setIsAvailable(false);
                car.setIsSold(false);
                car.setIsActive(true); // Reserved cars might still be visible (just not 'available')? Adjust if
                                       // needed.
                break;
            case "PROCESSING":
                // Processing = active but not yet ready (media uploading)
                car.setIsActive(false);
                car.setIsSold(false);
                car.setIsAvailable(false);
                break;
            case "INACTIVE":
            case "ARCHIVED":
                car.setIsActive(false);
                break;
            default:
                throw new IllegalArgumentException("Invalid status: " + status +
                        ". Valid statuses: AVAILABLE, SOLD, RESERVED, PROCESSING, INACTIVE, ARCHIVED");
        }

        car.setUpdatedAt(LocalDateTime.now());
        Car updatedCar = carRepository.save(car);

        return convertToResponseV2(updatedCar);
    }

    /**
     * Toggle vehicle visibility (Hide/Show)
     */
    public CarResponse toggleVisibility(String id, boolean visible, Long currentUserId) {
        log.debug("Toggling visibility for vehicle: {} to {} by user: {}", id, visible, currentUserId);

        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        // Check ownership or admin role
        if (!car.getOwner().getId().equals(currentUserId) && !isAdmin(currentUserId)) {
            throw new UnauthorizedActionException("You can only update your own vehicles");
        }

        if (visible && !car.getOwner().canListCarsPublicly()) {
            // Cannot enable visibility if not verified
            visible = false;
            log.info("Dealer {} is not verified, suppressing visibility toggle for vehicle {}", currentUserId, id);
            // Optionally throw exception or just suppress
        }

        car.setIsActive(visible);
        car.setUpdatedAt(LocalDateTime.now());

        Car updatedCar = carRepository.save(car);
        log.info("Vehicle visibility updated ID: {} to active: {}", id, visible);

        return convertToResponseV2(updatedCar);
    }

    /**
     * Update media status
     */
    public CarResponse updateMediaStatus(String id, MediaStatus status, Long currentUserId) {
        log.debug("Updating media status for vehicle: {} to {} by user: {}", id, status, currentUserId);

        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        // Check ownership or admin role
        if (!car.getOwner().getId().equals(currentUserId) && !isAdmin(currentUserId)) {
            throw new UnauthorizedActionException("You can only update your own vehicles");
        }

        car.setMediaStatus(status);

        // Auto-activate if READY
        if (status == MediaStatus.READY) {
            car.setIsAvailable(true);

            // Only set Active (Public) if dealer is verified/can list publicly
            if (car.getOwner().canListCarsPublicly()) {
                car.setIsActive(true);
            } else {
                car.setIsActive(false);
                log.info("Media ready for vehicle {}, but dealer {} is unverified. Keeping inactive.", id,
                        currentUserId);
            }

        } else if (status == MediaStatus.FAILED) {
            car.setIsActive(false);
        }

        car.setUpdatedAt(LocalDateTime.now());
        Car updatedCar = carRepository.save(car);

        return convertToResponseV2(updatedCar);
    }

    /**
     * Search vehicles with filters and optional free-text query.
     *
     * This method is used by /api/cars/search and is the main entry point for
     * the advanced search experience in the mobile app.
     */
    /**
     * Search vehicles with filters and optional free-text query.
     * Refactored to use JPA Specification to avoid LOWER(bytea) errors and improve
     * maintainability.
     */
    @Transactional(readOnly = true)
    public Page<CarResponse> searchVehicles(CarSearchCriteria criteria, Pageable pageable) {
        log.debug("Searching vehicles with criteria: {} and pageable: {}", criteria, pageable);

        Specification<Car> spec = com.carselling.oldcar.specification.CarSpecification.getCarsByCriteria(criteria);
        Page<Car> cars = carRepository.findAll(spec, pageable);

        // Apply owner role filtering and visibility checks (though most are now handled
        // by visibility checks in convert)
        // Note: The Specification handles the main filtering. Here we do final DTO
        // conversion and strict visibility checks.

        List<CarResponse> carResponses = cars.getContent().stream()
                .filter(this::isVehicleVisible) // Ensure basic visibility rules still apply
                .map(this::convertToResponseV2)
                .collect(Collectors.toList());

        return new PageImpl<>(carResponses, pageable, cars.getTotalElements());
    }

    /**
     * Get vehicle analytics
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

        return CarAnalyticsResponse.builder()
                .vehicleId(id)
                .views((long) (car.getViewCount() != null ? car.getViewCount() : 0))
                .inquiries((long) (car.getInquiryCount() != null ? car.getInquiryCount() : 0))
                .shares((long) (car.getShareCount() != null ? car.getShareCount() : 0))
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
    public CarResponse toggleFeatureVehicle(String id, boolean featured, Long currentUserId) {
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
     * Track vehicle inquiry
     */
    public void trackVehicleInquiry(String id) {
        log.debug("Tracking inquiry for vehicle: {}", id);

        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        car.incrementInquiryCount();
        carRepository.save(car);
    }

    /**
     * Track vehicle share
     */
    public void trackVehicleShare(String id, String platform) {
        log.debug("Tracking share for vehicle: {} on platform: {}", id, platform);

        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        car.incrementShareCount();
        carRepository.save(car);
    }

    /**
     * Get similar vehicles (mock implementation)
     */
    @Transactional(readOnly = true)
    public List<CarResponse> getSimilarVehicles(String id, int limit) {
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
    public Page<CarResponse> getVehiclesByDealer(String dealerId, String status, Pageable pageable) {
        log.debug("Getting vehicles by dealer: {} with status: {}", dealerId, status);

        Long dealerLongId = Long.parseLong(dealerId);
        Page<Car> cars = carRepository.findByOwnerId(dealerLongId, pageable);

        User currentUser = authService.getCurrentUserOrNull();
        boolean isDealerOwner = currentUser != null && currentUser.getId().equals(dealerLongId);

        List<Car> filteredCars;

        if (isDealerOwner) {
            filteredCars = cars.getContent();

            if (status != null && !status.isBlank()) {
                String normalized = status.trim().toUpperCase();
                filteredCars = filteredCars.stream().filter(car -> {
                    if ("SOLD".equals(normalized)) {
                        return Boolean.TRUE.equals(car.getIsSold());
                    }
                    if ("AVAILABLE".equals(normalized) || "ACTIVE".equals(normalized)) {
                        return Boolean.TRUE.equals(car.getIsActive()) && !Boolean.TRUE.equals(car.getIsSold());
                    }
                    if ("INACTIVE".equals(normalized)) {
                        return !Boolean.TRUE.equals(car.getIsActive());
                    }
                    return true;
                }).collect(Collectors.toList());
            }
        } else {
            filteredCars = cars.getContent().stream()
                    .filter(this::isVehicleVisible)
                    .collect(Collectors.toList());
        }

        List<CarResponse> carResponses = filteredCars.stream()
                .map(this::convertToResponseV2)
                .collect(Collectors.toList());

        return new PageImpl<>(carResponses, pageable, cars.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<CarResponse> getDealerCarsForOwner(Long dealerId, String status, Pageable pageable) {
        log.debug("Getting dealer-owned vehicles for user: {} with status: {}", dealerId, status);

        Page<Car> cars = carRepository.findByOwnerId(dealerId, pageable);

        List<Car> filtered = cars.getContent();
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase();
            filtered = filtered.stream().filter(car -> {
                if ("SOLD".equals(normalized)) {
                    return Boolean.TRUE.equals(car.getIsSold());
                }
                if ("AVAILABLE".equals(normalized) || "ACTIVE".equals(normalized)) {
                    return Boolean.TRUE.equals(car.getIsActive()) && !Boolean.TRUE.equals(car.getIsSold());
                }
                if ("INACTIVE".equals(normalized)) {
                    return !Boolean.TRUE.equals(car.getIsActive());
                }
                return true;
            }).collect(Collectors.toList());
        }

        List<CarResponse> carResponses = filtered.stream()
                .map(this::convertToResponseV2)
                .collect(Collectors.toList());

        return new PageImpl<>(carResponses, pageable, cars.getTotalElements());
    }

    // Helper methods

    private CarResponse convertToResponseV2(Car car) {
        return CarResponse.builder()
                .id(String.valueOf(car.getId()))
                .make(car.getMake())
                .model(car.getModel())
                .year(car.getYear())
                .price(car.getPrice() != null ? car.getPrice().longValue() : 0L)
                .mileage(car.getMileage() != null ? car.getMileage().longValue() : 0L)
                .location(car.getOwner().getLocation())
                .condition("Good") // Default value
                .images(car.getImages() != null ? car.getImages() : new java.util.ArrayList<>())
                .specifications(CarResponse.CarSpecifications.builder()
                        .fuelType(car.getFuelType())
                        .transmission(car.getTransmission())
                        .color(car.getColor())
                        .build())
                .dealerId(car.getOwner().getId().toString())
                .dealerName(car.getOwner().getUsername())
                .isCoListed(false)
                .coListedIn(List.of())
                .views(car.getViewCount() != null ? (long) car.getViewCount() : 0L)
                .inquiries(car.getInquiryCount() != null ? (long) car.getInquiryCount() : 0L)
                .shares(car.getShareCount() != null ? (long) car.getShareCount() : 0L)
                .status(getCarStatus(car))
                .mediaStatus(car.getMediaStatus() != null ? car.getMediaStatus().name() : "NONE")
                .featured(Boolean.TRUE.equals(car.getIsFeatured()) &&
                        (car.getFeaturedUntil() == null || car.getFeaturedUntil().isAfter(LocalDateTime.now())))
                .createdAt(car.getCreatedAt())
                .updatedAt(car.getUpdatedAt())
                .build();
    }

    private String getCarStatus(Car car) {
        if (Boolean.TRUE.equals(car.getIsSold())) {
            return "Sold";
        } else if (!Boolean.TRUE.equals(car.getIsAvailable()) && !Boolean.TRUE.equals(car.getIsActive())) {
            // Fallback for logic
            return "Processing";
        } else if (!Boolean.TRUE.equals(car.getIsAvailable()) && Boolean.TRUE.equals(car.getIsActive())) {
            // Active but not available = Reserved
            return "Reserved";
        } else if (Boolean.TRUE.equals(car.getIsActive())) {
            return "Available";
        } else {
            return "Archived";
        }
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(user -> "ADMIN".equals(user.getRole().name()))
                .orElse(false);
    }

    private boolean isVehicleVisible(Car car) {
        if (car == null || car.getOwner() == null)
            return false;

        User owner = car.getOwner();
        com.carselling.oldcar.model.Role role = owner.getRole();

        if (role == com.carselling.oldcar.model.Role.ADMIN) {
            return true;
        }

        if (role == com.carselling.oldcar.model.Role.USER) {
            return Boolean.TRUE.equals(car.getIsActive())
                    && !Boolean.TRUE.equals(car.getIsSold())
                    && car.getMediaStatus() == MediaStatus.READY;
        }

        if (role == com.carselling.oldcar.model.Role.DEALER) {
            return owner.canListCarsPublicly()
                    && Boolean.TRUE.equals(car.getIsActive())
                    && !Boolean.TRUE.equals(car.getIsSold())
                    && car.getMediaStatus() == MediaStatus.READY;
        }

        return false;
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

    /**
     * Increment specific car statistic
     */
    public void incrementCarStat(String id, String statType) {
        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        switch (statType) {
            case "video_play":
                car.incrementVideoPlayCount();
                break;
            case "image_swipe":
                car.incrementImageSwipeCount();
                break;
            case "contact_click":
                car.incrementContactClickCount();
                break;
            default:
                throw new IllegalArgumentException("Invalid stat type: " + statType);
        }

        carRepository.save(car);
    }
}
