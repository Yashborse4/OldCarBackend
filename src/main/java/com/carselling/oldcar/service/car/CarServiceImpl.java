package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.dto.car.CarRequest;
import com.carselling.oldcar.dto.car.PublicCarDTO;
import com.carselling.oldcar.dto.car.PrivateCarDTO;
import com.carselling.oldcar.dto.car.CarSearchCriteria;
import com.carselling.oldcar.dto.car.CarAnalyticsResponse;

import com.carselling.oldcar.dto.CarStatistics;
import com.carselling.oldcar.dto.user.UserSummary;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.CarStatus;
import com.carselling.oldcar.model.DealerStatus;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.MediaStatus;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.exception.UnauthorizedActionException;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.exception.BusinessException;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.repository.CarMasterRepository;
import com.carselling.oldcar.b2.B2FileService;
import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.model.UploadedFile;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.specification.CarSpecification;
import com.carselling.oldcar.service.auth.AuthService;
import com.carselling.oldcar.service.FileValidationService;
import com.carselling.oldcar.service.MediaFinalizationService;
import com.carselling.oldcar.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.multipart.MultipartFile;

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
    private final B2FileService b2FileService; // Injected instead of Firebase
    private final FileValidationService fileValidationService;
    private final MediaFinalizationService mediaFinalizationService;
    private final AuditLogService auditLogService;

    /**
     * Get all vehicles with pagination
     */
    public Page<CarResponse> getAllVehicles(Pageable pageable) {
        log.debug("Getting all vehicles with pagination: {}", pageable);

        User currentUser = authService.getCurrentUserOrNull();
        boolean isAdmin = currentUser != null && currentUser.getRole() == Role.ADMIN;

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
        boolean isAdmin = currentUser != null && currentUser.getRole() == Role.ADMIN;
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
                .status(car.getStatus() != null ? car.getStatus().name() : null)
                .mediaStatus(car.getMediaStatus() != null ? car.getMediaStatus().name() : null)
                .isSold(car.getIsSold())
                .isAvailable(car.getIsAvailable())
                .isApproved(car.getIsApproved())
                .viewCount(car.getViewCount())
                .location(car.getLocation())
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

        // Check for duplicate request using idempotency key
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            CarResponse existingCar = findByIdempotencyKey(idempotencyKey, currentUserId);
            if (existingCar != null) {
                log.info("Returning existing car for idempotency key: {}", idempotencyKey);
                return existingCar;
            }
        }

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
                .images(new ArrayList<>())
                .status(CarStatus.DRAFT)
                .mediaStatus(MediaStatus.INIT)
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

        Car savedCar = carRepository.save(car); // Save first to get ID for folder structure

        // Finalize Temporary Media if provided
        if (request.getTempFileIds() != null && !request.getTempFileIds().isEmpty()) {
            try {
                log.info("Finalizing {} temporary files for car {}", request.getTempFileIds().size(), savedCar.getId());
                String targetFolder = "cars/" + savedCar.getId() + "/images";

                List<UploadedFile> finalizedFiles = mediaFinalizationService
                        .finalizeUploads(request.getTempFileIds(), targetFolder,
                                ResourceType.CAR_IMAGE, savedCar.getId(), owner);

                List<String> finalizedUrls = finalizedFiles.stream()
                        .map(UploadedFile::getFileUrl)
                        .collect(Collectors.toList());

                car.setImages(finalizedUrls);
                car.setMediaStatus(MediaStatus.READY); // Single public visibility state

                // If we have images, set the first as banner
                if (!finalizedUrls.isEmpty()) {
                    car.setImageUrl(finalizedUrls.get(0));
                    car.setMediaStatus(MediaStatus.READY);
                    // If verified dealer, auto active? logic duplicated from uploadMedia
                    if (owner.canListCarsPublicly()) {
                        car.setIsActive(true);
                        car.setIsAvailable(true);
                    }
                }

                // Save again with images
                savedCar = carRepository.save(car);

            } catch (Exception e) {
                log.error("Failed to finalize media for car {}: {}", savedCar.getId(), e.getMessage());
                // Don't fail car creation? Or should we?
                // If media fails, car exists but has no images.
            }
        }

        // Legacy fallback:If imageUrl (Banner) is NOT provided, but we have images, use
        // the first
        // one as banner
        if ((car.getImageUrl() == null || car.getImageUrl().isBlank()) && !car.getImages().isEmpty()) {
            car.setImageUrl(car.getImages().get(0));
            carRepository.save(car); // Save update
        }

        log.info("Created new vehicle with ID: {} for user: {}", savedCar.getId(), currentUserId);
        auditLogService.logDataAccess("Car", savedCar.getId(), "CREATE", owner.getUsername(),
                "Vehicle created by user " + currentUserId);

        return convertToResponseV2(savedCar);
    }

    /**
     * Update vehicle with media (Facade method)
     */
    @Override
    public CarResponse updateVehicleWithMedia(String id, CarRequest request,
            List<MultipartFile> images, Long currentUserId) {

        // 1. Update vehicle details
        CarResponse response = updateVehicle(id, request, currentUserId);

        // 2. Upload and update media if provided
        if (images != null && !images.isEmpty()) {
            User uploader = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId.toString()));

            // Upload files using B2 service with correct resource type
            List<FileUploadResponse> uploads = b2FileService.uploadMultipleFiles(
                    images,
                    "cars/" + id + "/images",
                    uploader,
                    ResourceType.CAR_IMAGE,
                    Long.parseLong(id));

            List<String> imageUrls = uploads.stream()
                    .map(FileUploadResponse::getFileUrl)
                    .filter(url -> url != null && !url.isBlank())
                    .collect(Collectors.toList());

            if (imageUrls.isEmpty() && !uploads.isEmpty()) {
                // All uploads failed?
                FileUploadResponse firstError = uploads.get(0);
                throw new BusinessException("Failed to upload images: " + firstError.getFileName());
            }

            // Update car media
            response = uploadMedia(id, imageUrls, null, currentUserId);

        }

        return response;
    }

    /**
     * Update vehicle
     */
    public CarResponse updateVehicle(String id, CarRequest request, Long currentUserId) {
        log.debug("Updating vehicle: {} by user: {}", id, currentUserId);

        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        assertCanModifyCar(car, currentUserId, "update vehicles");

        // Update fields
        car.setMake(request.getMake());
        car.setModel(request.getModel());
        car.setYear(request.getYear());
        validatePriceChange(car, request.getPrice(), currentUserId);
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
        auditLogService.logDataAccess("Car", updatedCar.getId(), "UPDATE", getUsername(currentUserId),
                "Vehicle updated by user " + currentUserId);

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

        assertCanModifyCar(car, currentUserId, "delete vehicles");

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
        auditLogService.logDataAccess("Car", car.getId(), "DELETE", getUsername(currentUserId),
                "Vehicle hard deleted by user " + currentUserId);
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

        assertCanModifyCar(car, currentUserId, "update vehicles");

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
        try {
            CarStatus newStatus = CarStatus.valueOf(status.toUpperCase());
            car.setStatus(newStatus);

            // Sync legacy booleans based on status
            switch (newStatus) {
                case PUBLISHED: // Was AVAILABLE
                    car.setIsSold(false);
                    car.setIsAvailable(true);
                    if (car.getOwner().canListCarsPublicly()) {
                        car.setIsActive(true);
                    } else {
                        car.setIsActive(false); // Pending verification
                    }
                    break;
                case SOLD:
                    car.setIsSold(true);
                    car.setIsAvailable(false);
                    // car.setIsActive(true); // Sold cars can be visible
                    break;
                case RESERVED:
                    car.setIsAvailable(false);
                    car.setIsSold(false);
                    car.setIsActive(true);
                    break;
                case PROCESSING:
                    car.setIsActive(false);
                    car.setIsSold(false);
                    car.setIsAvailable(false);
                    break;
                case DRAFT:
                case ARCHIVED:
                    car.setIsActive(false);
                    car.setIsAvailable(false);
                    break;
                case DELETED:
                    car.setIsActive(false);
                    break;
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }

        car.setUpdatedAt(LocalDateTime.now());
        Car updatedCar = carRepository.save(car);
        auditLogService.logDataAccess("Car", updatedCar.getId(), "STATUS_UPDATE", getUsername(currentUserId),
                "Vehicle status updated to " + status + " by user " + currentUserId);

        return convertToResponseV2(updatedCar);
    }

    /**
     * Toggle vehicle visibility (Hide/Show)
     */
    public CarResponse toggleVisibility(String id, boolean visible, Long currentUserId) {
        log.debug("Toggling visibility for vehicle: {} to {} by user: {}", id, visible, currentUserId);

        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        assertCanModifyCar(car, currentUserId, "update vehicles");

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
        auditLogService.logDataAccess("Car", updatedCar.getId(), "VISIBILITY_UPDATE", getUsername(currentUserId),
                "Vehicle visibility set to " + visible + " by user " + currentUserId);

        return convertToResponseV2(updatedCar);
    }

    /**
     * Update media status
     */
    public CarResponse updateMediaStatus(String id, MediaStatus status, Long currentUserId) {
        log.debug("Updating media status for vehicle: {} to {} by user: {}", id, status, currentUserId);

        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        assertCanModifyCar(car, currentUserId, "update vehicles");

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
        auditLogService.logDataAccess("Car", updatedCar.getId(), "MEDIA_STATUS_UPDATE", getUsername(currentUserId),
                "Media status updated to " + status + " by user " + currentUserId);

        return convertToResponseV2(updatedCar);
    }

    /**
     * Upload Media Logic - Separated from Creation
     */
    public CarResponse uploadMedia(String id, java.util.List<String> imageUrls, String videoUrl, Long currentUserId) {
        log.info("Processing media upload for car: {} by user: {}", id, currentUserId);

        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        assertCanModifyCar(car, currentUserId, "upload media for");

        CarStatus status = car.getStatus();
        if (status == CarStatus.SOLD || status == CarStatus.ARCHIVED || status == CarStatus.DELETED) {
            throw new BusinessException("Cannot modify media when vehicle status is " + status.name());
        }

        // Validate URLs and cleanup replaced media
        if (imageUrls != null) {
            for (String url : imageUrls) {
                fileValidationService.validateFileUrl(url);
            }

            java.util.List<String> existingImages = car.getImages() != null
                    ? new ArrayList<>(car.getImages())
                    : new ArrayList<>();
            for (String existingUrl : existingImages) {
                if (existingUrl != null && !existingUrl.isBlank() && !imageUrls.contains(existingUrl)) {
                    try {
                        b2FileService.deleteFile(existingUrl);
                        log.debug("Deleted replaced image: {}", existingUrl);
                    } catch (Exception e) {
                        log.warn("Failed to delete replaced image: {} - {}", existingUrl, e.getMessage());
                    }
                }
            }

            car.setImages(imageUrls);

            // Set first image as banner if not set
            if (!imageUrls.isEmpty() && (car.getImageUrl() == null || car.getImageUrl().isBlank())) {
                car.setImageUrl(imageUrls.get(0));
            }
        }

        if (videoUrl != null && !videoUrl.isBlank()) {
            String existingVideo = car.getVideoUrl();
            if (existingVideo != null && !existingVideo.isBlank() && !existingVideo.equals(videoUrl)) {
                try {
                    b2FileService.deleteFile(existingVideo);
                    log.debug("Deleted replaced video: {}", existingVideo);
                } catch (Exception e) {
                    log.warn("Failed to delete replaced video: {} - {}", existingVideo, e.getMessage());
                }
            }
            fileValidationService.validateFileUrl(videoUrl);
            car.setVideoUrl(videoUrl);
        }

        // Update status flow: INIT -> READY (bypassing PROCESSING for synchronous
        // updates)
        // READY is the single state indicating media is complete and car is publicly
        // visible
        car.setMediaStatus(MediaStatus.READY);

        // Auto-activate if verified dealer
        if (car.getOwner().canListCarsPublicly()) {
            car.setIsActive(true);
            car.setIsAvailable(true);
        } else {
            log.info("Media completed for vehicle {}, but dealer {} is unverified. Keeping inactive.", id,
                    currentUserId);
        }

        car.setUpdatedAt(LocalDateTime.now());
        Car updatedCar = carRepository.save(car);
        auditLogService.logDataAccess("Car", updatedCar.getId(), "MEDIA_UPLOAD", getUsername(currentUserId),
                "Media uploaded for vehicle by user " + currentUserId);

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

        Specification<Car> spec = CarSpecification.getCarsByCriteria(criteria);
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

        assertCanModifyCar(car, currentUserId, "view analytics for");

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
        auditLogService.logDataAccess("Car", updatedCar.getId(), "FEATURE_TOGGLE", getUsername(currentUserId),
                "Vehicle featured flag set to " + featured + " by user " + currentUserId);
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

        car.setInquiryCount(car.getInquiryCount() != null ? (long) car.getInquiryCount() + 1L : 1L);
        carRepository.save(car);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarResponse> getVehiclesByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> longIds = ids.stream().map(Long::parseLong).collect(Collectors.toList());
        List<Car> cars = carRepository.findAllById(longIds);

        // Convert to DTOs
        return cars.stream()
                .map(this::convertToResponseV2)
                .collect(Collectors.toList());
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
                .description(car.getDescription())
                .images(car.getImages() != null ? new java.util.ArrayList<>(car.getImages())
                        : new java.util.ArrayList<>())
                .videoUrl(car.getVideoUrl())
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
                .isFeatured(Boolean.TRUE.equals(car.getIsFeatured()) &&
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
        Role role = owner.getRole();

        if (role == Role.ADMIN) {
            return true;
        }

        // READY is the single state indicating media is complete and car is publicly
        // visible
        if (role == Role.USER) {
            return Boolean.TRUE.equals(car.getIsActive())
                    && !Boolean.TRUE.equals(car.getIsSold())
                    && car.getMediaStatus() == MediaStatus.READY;
        }

        if (role == Role.DEALER) {
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

    /**
     * Finalize media for existing car
     */
    public CarResponse finalizeMedia(String id, java.util.List<Long> tempFileIds, Long currentUserId) {
        log.info("Finalizing media for car: {} by user: {}", id, currentUserId);

        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        assertCanModifyCar(car, currentUserId, "upload media for");

        CarStatus status = car.getStatus();
        if (status == CarStatus.SOLD || status == CarStatus.ARCHIVED || status == CarStatus.DELETED) {
            throw new BusinessException("Cannot modify media when vehicle status is " + status.name());
        }

        if (tempFileIds != null && !tempFileIds.isEmpty()) {
            try {
                log.info("Finalizing {} temporary files for car {}", tempFileIds.size(), id);
                String targetFolder = "cars/" + id + "/images";

                List<UploadedFile> finalizedFiles = mediaFinalizationService
                        .finalizeUploads(tempFileIds, targetFolder, ResourceType.CAR_IMAGE,
                                car.getId(), car.getOwner());

                List<String> finalizedUrls = finalizedFiles.stream()
                        .map(UploadedFile::getFileUrl)
                        .collect(Collectors.toList());

                // Append to existing images (handle null safely)
                List<String> currentImages = car.getImages() != null
                        ? new ArrayList<>(car.getImages())
                        : new ArrayList<>();
                currentImages.addAll(finalizedUrls);
                car.setImages(currentImages);

                car.setMediaStatus(MediaStatus.READY); // Single public visibility state

                // If we have images, ensure banner set
                if (!currentImages.isEmpty() && (car.getImageUrl() == null || car.getImageUrl().isBlank())) {
                    car.setImageUrl(currentImages.get(0));
                    car.setMediaStatus(MediaStatus.READY);

                    if (car.getOwner().canListCarsPublicly()) {
                        car.setIsActive(true);
                        car.setIsAvailable(true);
                    }
                }

                car.setUpdatedAt(LocalDateTime.now());
                Car updatedCar = carRepository.save(car);
                auditLogService.logDataAccess("Car", updatedCar.getId(), "MEDIA_FINALIZE", getUsername(currentUserId),
                        "Media finalized for vehicle by user " + currentUserId);
                return convertToResponseV2(updatedCar);

            } catch (Exception e) {
                log.error("Failed to finalize media for car {}: {}", id, e.getMessage());
                throw new RuntimeException("Media finalization failed", e);
            }
        }

        return convertToResponseV2(car);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isVehicleOwner(String id, Long userId) {
        return carRepository.existsByIdAndOwnerId(Long.parseLong(id), userId);
    }

    @Transactional(readOnly = true)
    public Long getCarOwnerId(String carId) {
        return carRepository.findById(Long.parseLong(carId))
                .map(car -> car.getOwner().getId())
                .orElse(null);
    }

    private String getUsername(Long userId) {
        if (userId == null) {
            return "SYSTEM";
        }
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElse("USER_" + userId);
    }

    private void assertCanModifyCar(Car car, Long currentUserId, String actionDescription) {
        if (currentUserId == null) {
            throw new UnauthorizedActionException("Authentication required to " + actionDescription);
        }
        if (!car.getOwner().getId().equals(currentUserId) && !isAdmin(currentUserId)) {
            throw new UnauthorizedActionException("You can only " + actionDescription + " your own vehicles");
        }
    }

    private void validatePriceChange(Car car, BigDecimal newPrice, Long currentUserId) {
        if (newPrice == null) {
            throw new BusinessException("Price is required");
        }

        if (newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Price must be greater than 0");
        }

        BigDecimal maxAllowed = new BigDecimal("999999999.99");
        if (newPrice.compareTo(maxAllowed) > 0) {
            throw new BusinessException("Price cannot exceed 999,999,999.99");
        }

        CarStatus status = car.getStatus();
        if (status == CarStatus.SOLD || status == CarStatus.ARCHIVED || status == CarStatus.DELETED) {
            throw new BusinessException("Cannot update price when vehicle status is " + status.name());
        }

        BigDecimal currentPrice = car.getPrice();
        if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal difference = newPrice.subtract(currentPrice).abs();
        if (difference.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        BigDecimal percentageChange = difference.multiply(BigDecimal.valueOf(100))
                .divide(currentPrice, 2, RoundingMode.HALF_UP);

        if (percentageChange.compareTo(BigDecimal.valueOf(80)) > 0 && !isAdmin(currentUserId)) {
            throw new BusinessException("Price change is too large compared to current price");
        }

        if (percentageChange.compareTo(BigDecimal.valueOf(40)) > 0) {
            auditLogService.logSecurityEvent("PRICE_CHANGE_LARGE", getUsername(currentUserId), null,
                    "Price changed from " + currentPrice + " to " + newPrice + " for car " + car.getId(),
                    "MEDIUM");
        } else {
            auditLogService.logDataAccess("Car", car.getId(), "PRICE_UPDATE", getUsername(currentUserId),
                    "Price changed from " + currentPrice + " to " + newPrice);
        }
    }
}
