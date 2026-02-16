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
import com.carselling.oldcar.service.ViewCountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final com.carselling.oldcar.repository.UploadedFileRepository uploadedFileRepository;
    private final com.carselling.oldcar.repository.TemporaryFileRepository temporaryFileRepository;
    private final MediaFinalizationService mediaFinalizationService;

    private final AuditLogService auditLogService;
    private final ViewCountService viewCountService;
    private final PlatformTransactionManager transactionManager;

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
     * Get vehicle by ID.
     * View count is incremented asynchronously via {@link ViewCountService}
     * to avoid a write bottleneck on every read.
     */
    @Transactional(readOnly = true)
    public CarResponse getVehicleById(String id) {
        log.debug("Getting vehicle by ID: {}", id);

        Car car = findCarAndEnsureVersion(parseCarId(id));

        if (car.getStatus() == CarStatus.DELETED) {
            throw new ResourceNotFoundException("Car", "id", id);
        }

        // Strict Access Control
        User currentUser = authService.getCurrentUserOrNull();
        boolean isOwner = currentUser != null && car.getOwner().getId().equals(currentUser.getId());
        boolean isAdmin = currentUser != null && currentUser.getRole() == Role.ADMIN;
        boolean isVerifiedPublic = isVehicleVisible(car);

        if (!isAdmin && !isOwner && !isVerifiedPublic) {
            // Throw 404 to hide existence of unverified/hidden cars
            throw new ResourceNotFoundException("Car", "id", id);
        }

        // Fire-and-forget: async bulk UPDATE instead of load-modify-save
        // Owner's own views are excluded by ViewCountService
        Long viewerUserId = currentUser != null ? currentUser.getId() : null;
        viewCountService.incrementAsync(car.getId(), viewerUserId, car.getOwner().getId());

        return convertToResponseV2(car);
    }

    /**
     * Get all public vehicles.
     * Cached for 2 minutes to reduce DB hits on the most-trafficked listing page.
     */
    @Cacheable(value = "publicCars", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.toString()")
    @Transactional(readOnly = true)
    public Page<PublicCarDTO> getPublicVehicles(Pageable pageable) {
        // Enforce visibility: USER = Always, DEALER = Only if verified using efficient
        // DB query
        Page<Car> cars = carRepository.findAllPublicCars(Role.USER, Role.DEALER, DealerStatus.VERIFIED, pageable);
        return cars.map(this::convertToPublicDTO);
    }

    /**
     * Get public vehicle by ID.
     * Cached for 5 minutes — individual car details change less often than
     * listings.
     */
    @Cacheable(value = "publicCarDetail", key = "#id")
    @Transactional(readOnly = true)
    public PublicCarDTO getPublicVehicleById(String id) {
        Car car = carRepository.findById(parseCarId(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        if (car.getStatus() == CarStatus.DELETED) {
            throw new ResourceNotFoundException("Car", "id", id);
        }

        if (!isVehicleVisible(car)) {
            throw new ResourceNotFoundException("Car", "id", id);
        }

        return convertToPublicDTO(car);
    }

    /**
     * Get private vehicle by ID (Authenticated).
     * View count is incremented asynchronously.
     */
    @Transactional(readOnly = true)
    public PrivateCarDTO getPrivateVehicleById(String id) {
        Car car = carRepository.findById(parseCarId(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        if (car.getStatus() == CarStatus.DELETED) {
            throw new ResourceNotFoundException("Car", "id", id);
        }

        // Fire-and-forget: async bulk UPDATE — owner views excluded
        User currentUser = authService.getCurrentUserOrNull();
        Long viewerUserId = currentUser != null ? currentUser.getId() : null;
        viewCountService.incrementAsync(car.getId(), viewerUserId, car.getOwner().getId());

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
     * Evicts public car caches so new listings appear immediately.
     */
    @Caching(evict = {
            @CacheEvict(value = "publicCars", allEntries = true),
            @CacheEvict(value = "publicCarDetail", allEntries = true)
    })
    public CarResponse createVehicle(CarRequest request, Long currentUserId, String idempotencyKey) {
        log.debug("Creating new vehicle for user: {} (idempotency: {})", currentUserId, idempotencyKey);

        // Validate and Sanitize
        validateMandatoryFields(request);
        validateCarRequest(request);

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
                // Sanitize URL: if empty/blank OR contains "temp", set to null to satisfy DB
                // constraint and logic
                .imageUrl(request.getImageUrl() != null && !request.getImageUrl().isBlank()
                        && !request.getImageUrl().contains("/temp/")
                                ? request.getImageUrl()
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
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .location(request.getLocation())
                .registrationNumber(request.getRegistrationNumber())
                // Initialize as INACTIVE until media is ready
                .isActive(false)
                .isAvailable(false)
                .isFeatured(false)
                .isSold(false)
                .viewCount(0L)
                .owner(owner)
                // Ignore images in initial creation - they must be uploaded via upload API
                .images(new ArrayList<>())
                .status(CarStatus.PROCESSING)
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

        // Set Co-Owner if provided
        if (request.getCoOwnerId() != null) {
            userRepository.findById(request.getCoOwnerId())
                    .ifPresent(coOwner -> {
                        car.setCoOwner(coOwner);
                        log.debug("Set co-owner for car: {} - {}", car.getId(), coOwner.getUsername());
                    });
        }

        Car savedCar = carRepository.save(car); // Save first to get ID for folder structure

        // Link Temporary Files to Car and Trigger Finalization
        if (request.getTempFileIds() != null && !request.getTempFileIds().isEmpty()) {

            // 1. Link TemporaryFiles to Car (Crucial for resume logic)
            // Using injected temporaryFileRepository
            List<com.carselling.oldcar.model.TemporaryFile> temporaryFiles = temporaryFileRepository
                    .findAllById(request.getTempFileIds());

            log.info("Found {} temporary files for car creation (IDs: {})", temporaryFiles.size(),
                    request.getTempFileIds());

            // Validate limits (Max 9 images, 1 video)
            long videoCount = temporaryFiles.stream()
                    .filter(f -> fileValidationService.isVideoFile(f.getOriginalFileName()))
                    .count();
            if (videoCount > 1) {
                throw new BusinessException("Maximum 1 video allowed per vehicle");
            }

            long imageCount = temporaryFiles.stream()
                    .filter(f -> fileValidationService.isImageFile(f.getOriginalFileName()))
                    .count();
            if (imageCount > 8) {
                throw new BusinessException("Maximum 8 images allowed per vehicle");
            }

            // 2. Set Car ID and Save
            for (com.carselling.oldcar.model.TemporaryFile tf : temporaryFiles) {
                tf.setCarId(savedCar.getId());
            }
            temporaryFileRepository.saveAll(temporaryFiles);
            log.info("Linked {} temporary files to Car ID: {}", temporaryFiles.size(), savedCar.getId());

            // 3. Trigger Async Finalization
            // Running in background thread to return response immediately
            // 3. Trigger Async Finalization AFTER transaction commit
            // This prevents race condition where async thread reads carId=null before
            // commit
            final Long carId = savedCar.getId();
            final List<Long> fileIds = request.getTempFileIds();
            final Long userId = currentUserId;

            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            java.util.concurrent.CompletableFuture.runAsync(() -> {
                                try {
                                    finalizeMedia(String.valueOf(carId), fileIds, userId);
                                } catch (Exception e) {
                                    log.error("Async media finalization failed for car {}", carId, e);
                                }
                            });
                        }
                    });

            // Return immediately with PROCESSING status (set during build)
            return convertToResponseV2(savedCar);
        }

        log.info("Created new vehicle with ID: {} for user: {}", savedCar.getId(), currentUserId);
        auditLogService.logDataAccess("Car", savedCar.getId(), "CREATE", owner.getUsername(),
                "Vehicle created by user " + currentUserId);

        return convertToResponseV2(savedCar);
    }

    /**
     * Finalizes media for a car.
     * Designed to be idempotent and resumable.
     */
    public CarResponse finalizeMedia(String carIdStr, List<Long> tempFileIds, Long currentUserId) {
        Long carId = parseCarId(carIdStr);
        Car car = findCarAndEnsureVersion(carId);
        User owner = car.getOwner();

        // 1. Security check
        if (currentUserId != null && !owner.getId().equals(currentUserId)) {
            throw new SecurityException("You do not own this vehicle");
        }

        if (car.getStatus() == CarStatus.ARCHIVED) {
            throw new BusinessException("Cannot update media when vehicle status is ARCHIVED");
        }

        // 2. Limit Validation (Max 8 Images, 1 Video)
        List<com.carselling.oldcar.model.TemporaryFile> tempFiles = temporaryFileRepository.findAllById(tempFileIds);

        long newVideos = tempFiles.stream()
                .filter(f -> fileValidationService.isVideoFile(f.getOriginalFileName()))
                .count();

        long newImages = tempFiles.stream()
                .filter(f -> fileValidationService.isImageFile(f.getOriginalFileName()))
                .count();

        // Check Video Limit
        boolean hasExistingVideo = car.getVideoUrl() != null && !car.getVideoUrl().isBlank();
        if (newVideos > 0 && (hasExistingVideo || newVideos > 1)) {
            throw new BusinessException("Maximum 1 video allowed per vehicle. Please delete the existing video first.");
        }

        // Check Image Limit
        int currentImageCount = car.getImages() != null ? car.getImages().size() : 0;
        if (currentImageCount + newImages > 8) {
            throw new BusinessException(
                    String.format("Maximum 8 images allowed. Existing: %d, New: %d", currentImageCount, newImages));
        }

        try {
            log.info("Finalizing media for car {}", carId);
            // Change: Pass base folder only. MediaFinalizationService will append /images
            // or /videos
            String targetFolder = "cars/" + carId;

            List<UploadedFile> finalizedFiles = mediaFinalizationService
                    .finalizeUploads(tempFileIds, targetFolder,
                            ResourceType.CAR_IMAGE, carId, owner);

            if (finalizedFiles.isEmpty()) {
                log.warn("Media finalization returned no files for car {}.", carId);
                // If we were expecting files but got none, strictly we might want to error,
                // but for now we just return the car as is.
            } else {
                // Separate new images and videos
                List<String> newImageUrls = finalizedFiles.stream()
                        .filter(f -> fileValidationService.isImageFile(f.getFileName()))
                        .map(UploadedFile::getFileUrl)
                        .collect(Collectors.toList());

                String newVideoUrl = finalizedFiles.stream()
                        .filter(f -> fileValidationService.isVideoFile(f.getFileName()))
                        .map(UploadedFile::getFileUrl)
                        .findFirst()
                        .orElse(null);

                // 3. Append Images
                if (!newImageUrls.isEmpty()) {
                    List<String> allImages = car.getImages() != null ? new ArrayList<>(car.getImages())
                            : new ArrayList<>();
                    allImages.addAll(newImageUrls);
                    car.setImages(allImages);

                    // Set first image as banner if valid
                    if (!allImages.isEmpty() && (car.getImageUrl() == null || car.getImageUrl().isBlank())) {
                        car.setImageUrl(allImages.get(0));
                    }
                }

                // 4. Set Video
                if (newVideoUrl != null) {
                    car.setVideoUrl(newVideoUrl);
                }

                car.setMediaStatus(MediaStatus.READY);
                autoActivateIfEligible(car);
            }

            Car savedCar = carRepository.save(car);
            return convertToResponseV2(savedCar);

        } catch (SecurityException e) {
            log.error("Security violation during media finalization for car {}: {}", carId, e.getMessage());
            car.setMediaStatus(MediaStatus.FAILED);
            car.setStatus(CarStatus.DRAFT);
            carRepository.save(car);
            throw e;
        } catch (Exception e) {
            log.error("Transient error finalizing media for car {}: {}. Will be retried.", carId, e.getMessage());
            throw new BusinessException("Media finalization failed: " + e.getMessage());
        }
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

            // Validate Count and Types
            if (images.size() > 8) {
                throw new BusinessException("Maximum 8 images allowed per vehicle");
            }

            // Ensure all are images
            for (MultipartFile img : images) {
                if (fileValidationService.isVideoFile(img.getOriginalFilename())) {
                    throw new BusinessException(
                            "Videos must be uploaded separated via videoUrl field, not in images list");
                }
                if (!fileValidationService.isImageFile(img.getOriginalFilename())) {
                    throw new BusinessException("Only image files are allowed in this field");
                }
            }

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
     * Update vehicle.
     * Evicts public car caches so updates are reflected immediately.
     */
    @Caching(evict = {
            @CacheEvict(value = "publicCars", allEntries = true),
            @CacheEvict(value = "publicCarDetail", allEntries = true)
    })
    public CarResponse updateVehicle(String id, CarRequest request, Long currentUserId) {
        log.debug("Updating vehicle: {} by user: {}", id, currentUserId);

        // Validate request inputs
        validateCarRequest(request);

        // Additional validation specific to updates if needed, but generic validation
        // covers basics

        Car car = findCarAndEnsureVersion(parseCarId(id));

        assertCanModifyCar(car, currentUserId, "update vehicles");

        // Update fields (Partial Update - Check for Nulls)
        if (request.getMake() != null)
            car.setMake(request.getMake());
        if (request.getModel() != null)
            car.setModel(request.getModel());
        if (request.getYear() != null)
            car.setYear(request.getYear());

        if (request.getPrice() != null) {
            validatePriceChange(car, request.getPrice(), currentUserId);
            car.setPrice(request.getPrice());
        }

        if (request.getDescription() != null)
            car.setDescription(request.getDescription());
        if (request.getMileage() != null)
            car.setMileage(request.getMileage());
        if (request.getFuelType() != null)
            car.setFuelType(request.getFuelType());
        if (request.getTransmission() != null)
            car.setTransmission(request.getTransmission());
        if (request.getColor() != null)
            car.setColor(request.getColor());
        if (request.getNumberOfOwners() != null)
            car.setNumberOfOwners(request.getNumberOfOwners());

        if (request.getAccidentHistory() != null)
            car.setAccidentHistory(request.getAccidentHistory());
        if (request.getRepaintedParts() != null)
            car.setRepaintedParts(request.getRepaintedParts());
        if (request.getEngineIssues() != null)
            car.setEngineIssues(request.getEngineIssues());
        if (request.getFloodDamage() != null)
            car.setFloodDamage(request.getFloodDamage());
        if (request.getInsuranceClaims() != null)
            car.setInsuranceClaims(request.getInsuranceClaims());
        if (request.getVariant() != null)
            car.setVariant(request.getVariant());
        if (request.getUsage() != null)
            car.setUsage(request.getUsage());
        if (request.getLatitude() != null)
            car.setLatitude(request.getLatitude());
        if (request.getLongitude() != null)
            car.setLongitude(request.getLongitude());
        if (request.getLocation() != null)
            car.setLocation(request.getLocation());
        if (request.getRegistrationNumber() != null)
            car.setRegistrationNumber(request.getRegistrationNumber());

        // Update Co-Owner if provided
        if (request.getCoOwnerId() != null) {
            userRepository.findById(request.getCoOwnerId()).ifPresent(car::setCoOwner);
        }

        // Validate and set Video URL
        // Validate and set Video URL
        updateCarVideo(car, request.getVideoUrl());

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
     * Delete vehicle.
     * Performs a SOFT DELETE in Database (Transactional), then HARD DELETE in
     * Storage (Non-Transactional).
     * Uses NOT_SUPPORTED propagation to ensure B2 cleanup doesn't hold a DB
     * transaction open.
     */
    @Caching(evict = {
            @CacheEvict(value = "publicCars", allEntries = true),
            @CacheEvict(value = "publicCarDetail", allEntries = true)
    })
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void deleteVehicle(String id, Long currentUserId, boolean hard) {
        log.debug("Deleting vehicle: {} by user: {}", id, currentUserId);

        Long carId = parseCarId(id);

        // 1. Perform Soft Delete (DB) in a short, isolated transaction
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.execute(status -> {
            Car car = findCarAndEnsureVersion(carId);
            assertCanModifyCar(car, currentUserId, "delete vehicles");

            car.setStatus(CarStatus.DELETED);
            car.setIsActive(false);
            car.setIsAvailable(false);
            car.setIsSold(false); // Can't be sold if deleted
            car.setUpdatedAt(LocalDateTime.now());
            car.setImageUrl(null); // Clear image refs
            car.setVideoUrl(null);
            car.setImages(new ArrayList<>()); // Clear image list

            carRepository.save(car);

            auditLogService.logDataAccess("Car", car.getId(), "DELETE", getUsername(currentUserId),
                    "Vehicle soft deleted (storage queued for wipe) by user " + currentUserId);
            return null;
        });

        // 2. Hard Cleanup of Storage (Folder level)
        // Executed OUTSIDE the transaction to prevent optimistic locking failures if
        // this takes time
        try {
            log.info("Cleaning up all media for deleted car: {}", id);
            mediaFinalizationService.cleanupCarMedia(carId);
        } catch (Exception e) {
            log.error("Error during storage cleanup for car {}: {}", id, e.getMessage());
            // Continue; DB is already consistent
        }

        log.info("Soft deleted vehicle with ID: {} by user: {}", id, currentUserId);
    }

    /**
     * Soft delete all cars for a specific user (Admin only internal use)
     */
    public void softDeleteUserCars(Long userId) {
        log.debug("Soft deleting all cars for user: {}", userId);
        carRepository.softDeleteCarsByOwner(userId);
    }

    /**
     * Update vehicle status.
     * Evicts caches since status changes affect public visibility.
     */
    @Caching(evict = {
            @CacheEvict(value = "publicCars", allEntries = true),
            @CacheEvict(value = "publicCarDetail", allEntries = true)
    })
    public CarResponse updateVehicleStatus(String id, String status, Long currentUserId) {
        log.debug("Updating vehicle status: {} to {} by user: {}", id, status, currentUserId);

        Car car = findCarAndEnsureVersion(parseCarId(id));

        assertCanModifyCar(car, currentUserId, "update vehicles");

        // Check dealer verification for public visibility
        boolean isPublishing = "PUBLISHED".equalsIgnoreCase(status) || "AVAILABLE".equalsIgnoreCase(status);
        if (isPublishing && !car.getOwner().canListCarsPublicly()) {
            log.warn("Unverified dealer {} attempting to publish car {}. Visibility will remain restricted.",
                    currentUserId, id);
            // We allow the status change to proceed (it will be mapped to PUBLISHED below
            // if 'AVAILABLE' is sent, or strictly validated),
            // but the visibility (isActive) will be suppressed below in the switch case.
        }

        // Map legacy 'AVAILABLE' to 'PUBLISHED' if necessary, or let valueOf handle it
        if ("AVAILABLE".equalsIgnoreCase(status)) {
            status = "PUBLISHED";
        }

        // Update status based on string value
        try {
            CarStatus newStatus = CarStatus.valueOf(status.toUpperCase());

            // Validate status transition is allowed
            if (car.getStatus() != null) {
                validateStatusTransition(car.getStatus(), newStatus);
            }

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
                case PENDING_VERIFICATION:
                    car.setIsActive(false);
                    car.setIsAvailable(true);
                    car.setIsSold(false);
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

        Car car = findCarAndEnsureVersion(Long.parseLong(id));

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

        Car car = findCarAndEnsureVersion(Long.parseLong(id));

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

        Car car = findCarAndEnsureVersion(Long.parseLong(id));

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
                        // Delete from B2
                        b2FileService.deleteFile(existingUrl);
                        log.debug("Deleted replaced image from B2: {}", existingUrl);

                        // Delete DB record to prevent orphans
                        uploadedFileRepository.findByFileUrl(existingUrl)
                                .ifPresent(uploadedFileRepository::delete);

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
            updateCarVideo(car, videoUrl);
        }

        // Update status flow: INIT -> READY (bypassing PROCESSING for synchronous
        // updates)
        // READY is the single state indicating media is complete and car is publicly
        // visible
        car.setMediaStatus(MediaStatus.READY);

        autoActivateIfEligible(car);

        car.setUpdatedAt(LocalDateTime.now());
        Car updatedCar = carRepository.save(car);
        auditLogService.logDataAccess("Car", updatedCar.getId(), "MEDIA_UPLOAD", getUsername(currentUserId),
                "Media uploaded for vehicle by user " + currentUserId);

        return convertToResponseV2(updatedCar);
    }

    // ==================== GRANULAR MEDIA MANAGEMENT ====================

    /**
     * Delete a single image at the given index from a car's image list.
     * Deletes the file from B2 storage and the DB record.
     * If the deleted image was the banner, the next image becomes the banner.
     *
     * @param carId         Car ID
     * @param imageIndex    Zero-based index of the image to delete
     * @param currentUserId ID of the requesting user
     * @return Updated car response
     */
    @Override
    @Transactional
    public CarResponse deleteCarImage(String carId, int imageIndex, Long currentUserId) {
        log.info("Deleting image at index {} from car {} by user {}", imageIndex, carId, currentUserId);

        Car car = findCarAndEnsureVersion(Long.parseLong(carId));
        assertCanModifyCar(car, currentUserId, "delete image from");

        List<String> images = car.getImages() != null ? new ArrayList<>(car.getImages()) : new ArrayList<>();
        if (imageIndex < 0 || imageIndex >= images.size()) {
            throw new BusinessException(
                    "Image index " + imageIndex + " is out of bounds. Car has " + images.size() + " images.");
        }

        String deletedUrl = images.remove(imageIndex);

        // Cleanup from B2 storage and DB
        cleanupMediaFile(deletedUrl);

        car.setImages(images);

        // Update banner: if we deleted the cover (index 0), set next image as banner
        if (images.isEmpty()) {
            car.setImageUrl(null);
        } else {
            car.setImageUrl(images.get(0));
        }

        car.setUpdatedAt(LocalDateTime.now());
        Car updatedCar = carRepository.save(car);
        auditLogService.logDataAccess("Car", updatedCar.getId(), "IMAGE_DELETE", getUsername(currentUserId),
                "Deleted image at index " + imageIndex + " by user " + currentUserId);

        return convertToResponseV2(updatedCar);
    }

    /**
     * Replace a single image at the given index with a new image URL.
     * The old image is deleted from B2 storage and the DB record.
     *
     * @param carId         Car ID
     * @param imageIndex    Zero-based index of the image to replace
     * @param newImageUrl   URL of the new image (must already be uploaded)
     * @param currentUserId ID of the requesting user
     * @return Updated car response
     */
    @Override
    @Transactional
    public CarResponse replaceCarImage(String carId, int imageIndex, String newImageUrl, Long currentUserId) {
        log.info("Replacing image at index {} for car {} by user {}", imageIndex, carId, currentUserId);

        Car car = findCarAndEnsureVersion(Long.parseLong(carId));
        assertCanModifyCar(car, currentUserId, "replace image on");

        List<String> images = car.getImages() != null ? new ArrayList<>(car.getImages()) : new ArrayList<>();
        if (imageIndex < 0 || imageIndex >= images.size()) {
            throw new BusinessException(
                    "Image index " + imageIndex + " is out of bounds. Car has " + images.size() + " images.");
        }

        fileValidationService.validateFileUrl(newImageUrl);

        String oldUrl = images.get(imageIndex);
        images.set(imageIndex, newImageUrl);

        // Cleanup the replaced image from B2 and DB
        cleanupMediaFile(oldUrl);

        car.setImages(images);

        // Update banner if we replaced the cover image (index 0)
        if (imageIndex == 0) {
            car.setImageUrl(newImageUrl);
        }

        car.setUpdatedAt(LocalDateTime.now());
        Car updatedCar = carRepository.save(car);
        auditLogService.logDataAccess("Car", updatedCar.getId(), "IMAGE_REPLACE", getUsername(currentUserId),
                "Replaced image at index " + imageIndex + " by user " + currentUserId);

        return convertToResponseV2(updatedCar);
    }

    /**
     * Delete the video from a car listing.
     * Removes the file from B2 storage and clears the videoUrl field.
     *
     * @param carId         Car ID
     * @param currentUserId ID of the requesting user
     * @return Updated car response
     */
    @Override
    @Transactional
    public CarResponse deleteCarVideo(String carId, Long currentUserId) {
        log.info("Deleting video from car {} by user {}", carId, currentUserId);

        Car car = findCarAndEnsureVersion(Long.parseLong(carId));
        assertCanModifyCar(car, currentUserId, "delete video from");

        String oldVideoUrl = car.getVideoUrl();
        if (oldVideoUrl == null || oldVideoUrl.isBlank()) {
            throw new BusinessException("Car has no video to delete.");
        }

        // Cleanup from B2 and DB
        cleanupMediaFile(oldVideoUrl);

        car.setVideoUrl(null);
        car.setUpdatedAt(LocalDateTime.now());
        Car updatedCar = carRepository.save(car);
        auditLogService.logDataAccess("Car", updatedCar.getId(), "VIDEO_DELETE", getUsername(currentUserId),
                "Deleted video by user " + currentUserId);

        return convertToResponseV2(updatedCar);
    }

    /**
     * Set the image at the given index as the banner (cover) image.
     * Moves the image to position 0 in the list and updates imageUrl.
     *
     * @param carId         Car ID
     * @param imageIndex    Zero-based index of the image to promote
     * @param currentUserId ID of the requesting user
     * @return Updated car response
     */
    @Override
    @Transactional
    public CarResponse updateCarBanner(String carId, int imageIndex, Long currentUserId) {
        log.info("Setting image at index {} as banner for car {} by user {}", imageIndex, carId, currentUserId);

        Car car = findCarAndEnsureVersion(Long.parseLong(carId));
        assertCanModifyCar(car, currentUserId, "update banner of");

        List<String> images = car.getImages() != null ? new ArrayList<>(car.getImages()) : new ArrayList<>();
        if (imageIndex < 0 || imageIndex >= images.size()) {
            throw new BusinessException(
                    "Image index " + imageIndex + " is out of bounds. Car has " + images.size() + " images.");
        }

        if (imageIndex == 0) {
            // Already the banner, no-op
            return convertToResponseV2(car);
        }

        // Move image to front
        String promoted = images.remove(imageIndex);
        images.add(0, promoted);

        car.setImages(images);
        car.setImageUrl(promoted);

        car.setUpdatedAt(LocalDateTime.now());
        Car updatedCar = carRepository.save(car);
        auditLogService.logDataAccess("Car", updatedCar.getId(), "BANNER_UPDATE", getUsername(currentUserId),
                "Set image at index " + imageIndex + " as banner by user " + currentUserId);

        return convertToResponseV2(updatedCar);
    }

    /**
     * Helper: Cleanup a media file from B2 storage and the UploadedFile DB record.
     * Logs warnings but does not throw on cleanup failure.
     *
     * @param fileUrl The URL of the file to delete
     */
    private void cleanupMediaFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank())
            return;
        try {
            b2FileService.deleteFile(fileUrl);
            log.debug("Deleted media file from B2: {}", fileUrl);
        } catch (Exception e) {
            log.warn("Failed to delete media file from B2: {} - {}", fileUrl, e.getMessage());
        }
        try {
            uploadedFileRepository.findByFileUrl(fileUrl)
                    .ifPresent(uploadedFileRepository::delete);
        } catch (Exception e) {
            log.warn("Failed to delete UploadedFile record for: {} - {}", fileUrl, e.getMessage());
        }
    }

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
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private com.carselling.oldcar.service.analytics.UserAnalyticsService userAnalyticsService;

    @Transactional(readOnly = true)
    public CarAnalyticsResponse getVehicleAnalytics(String id, Long currentUserId) {
        log.debug("Getting analytics for vehicle: {} by user: {}", id, currentUserId);

        Car car = carRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id));

        assertCanModifyCar(car, currentUserId, "view analytics for");

        // Delegate to UserAnalyticsService for event-sourced metrics
        // This ensures consistent data with the dashboard and excludes owner views
        return userAnalyticsService.getCarAnalytics(car.getId());
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
    @org.springframework.scheduling.annotation.Async
    @Transactional
    public void trackVehicleView(String id) {
        log.debug("Tracking view for vehicle: {}", id);
        try {
            Long carId = parseCarId(id);
            carRepository.incrementViewCount(carId);
        } catch (Exception e) {
            log.warn("Failed to increment view count for car {}: {}", id, e.getMessage());
        }
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
        Page<Car> cars = carRepository.findByOwnerIdOrCoOwnerId(dealerLongId, dealerLongId, pageable);

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

        Page<Car> cars = carRepository.findByOwnerIdOrCoOwnerId(dealerId, dealerId, pageable);

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
                .coOwnerId(car.getCoOwner() != null ? car.getCoOwner().getId().toString() : null)
                .coOwnerName(car.getCoOwner() != null ? car.getCoOwner().getUsername() : null)
                .isCoListed(car.getCoOwner() != null)
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
        // Use the entity method which we updated to include co-owner check
        if (!car.isOwnedBy(currentUserId) && !isAdmin(currentUserId)) {
            throw new UnauthorizedActionException(
                    "You can only " + actionDescription + " your own vehicles (or co-listed)");
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

    private void autoActivateIfEligible(Car car) {
        if (car.getOwner().canListCarsPublicly()) {
            // Verified Dealer: Auto-activate and Publish
            car.setIsActive(true);
            car.setIsAvailable(true);

            // If it was PROCESSING, DRAFT (Legacy) or PENDING_VERIFICATION, move to
            // PUBLISHED
            if (car.getStatus() == CarStatus.PROCESSING || car.getStatus() == CarStatus.DRAFT
                    || car.getStatus() == CarStatus.PENDING_VERIFICATION) {
                car.setStatus(CarStatus.PUBLISHED);
            }
        } else {
            // Unverified Dealer: Keep Inactive (Pending Verification)
            car.setIsActive(false);

            // Explicitly set as PENDING_VERIFICATION if it was DRAFT, PUBLISHED, or
            // PROCESSING
            if (car.getStatus() == CarStatus.DRAFT || car.getStatus() == CarStatus.PUBLISHED
                    || car.getStatus() == CarStatus.PROCESSING) {
                car.setStatus(CarStatus.PENDING_VERIFICATION);
            }
            log.info("Dealer {} is unverified. Car {} moved to PENDING_VERIFICATION state.",
                    car.getOwner().getId(), car.getId());
        }
    }

    /**
     * Activate all cars for a verify dealer (Called when dealer is verified)
     */
    @Transactional
    public void activateDealerCars(Long dealerId) {
        log.info("Activating all pending cars for newly verified dealer: {}", dealerId);

        // Find all cars by this dealer with PENDING_VERIFICATION or PROCESSING
        Specification<Car> spec = (root, query, cb) -> {
            return cb.and(
                    cb.equal(root.get("owner").get("id"), dealerId),
                    cb.or(
                            cb.equal(root.get("status"), CarStatus.PENDING_VERIFICATION),
                            cb.equal(root.get("status"), CarStatus.PROCESSING)));
        };

        List<Car> pendingCars = carRepository.findAll(spec);

        for (Car car : pendingCars) {
            // Only activate if media is ready
            if (car.getMediaStatus() == MediaStatus.READY) {
                car.setStatus(CarStatus.PUBLISHED);
                car.setIsActive(true);
                car.setIsAvailable(true);
                car.setUpdatedAt(LocalDateTime.now());
                log.info("Auto-publishing car {} for verified dealer {}", car.getId(), dealerId);
            } else {
                log.info("Skipping auto-publish for car {} - Media not ready", car.getId());
            }
        }

        carRepository.saveAll(pendingCars);
    }

    private void updateCarVideo(Car car, String newVideoUrl) {
        if (newVideoUrl != null && !newVideoUrl.isBlank()) {
            String oldVideoUrl = car.getVideoUrl();
            if (oldVideoUrl != null && !oldVideoUrl.isBlank() && !oldVideoUrl.equals(newVideoUrl)) {
                try {
                    b2FileService.deleteFile(oldVideoUrl);
                    log.debug("Deleted replaced video: {}", oldVideoUrl);
                } catch (Exception e) {
                    log.warn("Failed to delete replaced video: {}", oldVideoUrl);
                }
            }
            fileValidationService.validateFileUrl(newVideoUrl);
            car.setVideoUrl(newVideoUrl);
        } else if (newVideoUrl != null && newVideoUrl.isEmpty()) {
            // Explicitly allow clearing if empty string is passed
            String oldVideoUrl = car.getVideoUrl();
            if (oldVideoUrl != null && !oldVideoUrl.isBlank()) {
                try {
                    b2FileService.deleteFile(oldVideoUrl);
                } catch (Exception e) {
                    log.warn("Falied to delete old video: {}", oldVideoUrl);
                }
            }
            car.setVideoUrl(null);
        }
    }

    // ==================== ROBUST ERROR HANDLING HELPERS ====================

    /**
     * Safely parse car ID string to Long.
     * Throws BusinessException with user-friendly message for invalid IDs.
     */
    private Long parseCarId(String id) {
        if (id == null || id.isBlank()) {
            throw new BusinessException("Vehicle ID is required");
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("Invalid vehicle ID format: " + id);
        }
    }

    /**
     * Validate CreateCarRequest input fields.
     */
    /**
     * Removed legacy validateCreateCarRequest in favor of generic
     * validateCarRequest
     */
    // private void validateCreateCarRequest(CreateCarRequest request) {
    // if (request == null) {
    // throw new BusinessException("Vehicle data is required");
    // }
    // ...
    // }

    /**
     * Validate UpdateCarRequest input fields.
     */
    /**
     * Removed legacy validateUpdateCarRequest in favor of generic
     * validateCarRequest
     */
    // private void validateUpdateCarRequest(UpdateCarRequest request) {
    // ...
    // }
    // if(request==null)

    // // Only validate fields if they are present (not null)
    // int currentYear = LocalDateTime.now().getYear();if(request.getYear()!=null)
    // {
    // if (request.getYear() < 1900 || request.getYear() > currentYear + 1) {
    // throw new BusinessException("Invalid year. Must be between 1900 and " +
    // (currentYear + 1));
    // if(request==null){throw new BusinessException("Vehicle data is required");}
    // // Only validate fields if they are present (not null)
    // int currentYear = LocalDateTime.now().getYear();if(request.getYear()!=null)
    // {
    // if (request.getYear() < 1900 || request.getYear() > currentYear + 1) {
    // throw new BusinessException("Invalid year. Must be between 1900 and " +
    // (currentYear + 1));
    // }
    // }if(request.getMileage()!=null)
    // {
    // if (request.getMileage() < 0 || request.getMileage() > 9999999) {
    // throw new BusinessException("Invalid mileage. Must be between 0 and 9,999,999
    // km");
    // }
    // }
    // }

    /**
     * Validate car request input fields.
     */
    private void validateCarRequest(CarRequest request) {
        if (request == null) {
            throw new BusinessException("Vehicle data is required");
        }

        // Year validation: 1900 to next year
        int currentYear = LocalDateTime.now().getYear();
        if (request.getYear() != null) {
            if (request.getYear() < 1900 || request.getYear() > currentYear + 1) {
                throw new BusinessException("Invalid year. Must be between 1900 and " + (currentYear + 1));
            }
        }

        // Mileage validation: 0 to 9,999,999 km
        if (request.getMileage() != null) {
            if (request.getMileage() < 0 || request.getMileage() > 9999999) {
                throw new BusinessException("Invalid mileage. Must be between 0 and 9,999,999 km");
            }
        }
    }

    /**
     * Validate mandatory fields for creation.
     */
    private void validateMandatoryFields(CarRequest request) {
        if (request == null) {
            throw new BusinessException("Vehicle data is required");
        }

        // Make validation
        if (request.getMake() == null || request.getMake().isBlank()) {
            throw new BusinessException("Vehicle make is required");
        }

        // Model validation
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new BusinessException("Vehicle model is required");
        }
    }

    /**
     * Status transition state machine.
     * Defines which status transitions are allowed.
     */
    private static final Map<CarStatus, Set<CarStatus>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            CarStatus.PROCESSING,
            EnumSet.of(CarStatus.PUBLISHED, CarStatus.PENDING_VERIFICATION, CarStatus.ARCHIVED, CarStatus.DELETED),
            CarStatus.PENDING_VERIFICATION, EnumSet.of(CarStatus.PUBLISHED, CarStatus.ARCHIVED, CarStatus.DELETED),
            CarStatus.PUBLISHED, EnumSet.of(CarStatus.SOLD, CarStatus.RESERVED, CarStatus.ARCHIVED, CarStatus.DELETED),
            CarStatus.SOLD, EnumSet.of(CarStatus.ARCHIVED, CarStatus.DELETED),
            CarStatus.RESERVED, EnumSet.of(CarStatus.PUBLISHED, CarStatus.SOLD, CarStatus.DELETED),
            CarStatus.ARCHIVED, EnumSet.of(CarStatus.PUBLISHED, CarStatus.DELETED),
            CarStatus.DRAFT, EnumSet.of(CarStatus.PROCESSING, CarStatus.PUBLISHED, CarStatus.DELETED),
            CarStatus.DELETED, EnumSet.noneOf(CarStatus.class) // Terminal state
    );

    /**
     * Validate status transition is allowed.
     */
    private void validateStatusTransition(CarStatus currentStatus, CarStatus newStatus) {
        if (currentStatus == newStatus) {
            return; // No change, always allowed
        }

        Set<CarStatus> allowed = ALLOWED_STATUS_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new BusinessException(
                    String.format("Cannot transition from %s to %s. Allowed transitions: %s",
                            currentStatus, newStatus,
                            allowed != null ? allowed : "none"));
        }
    }

    private Car findCarAndEnsureVersion(Long id) {
        Car car = carRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id.toString()));

        if (car.getVersion() == null) {
            try {
                log.warn("Car {} has null version. Initializing to 0 to prevent optimistic locking failure.", id);
                carRepository.initializeVersion(id);
                // Reload to get the version
                return carRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Car", "id", id.toString()));
            } catch (Exception e) {
                log.error("Failed to initialize version for car {}: {}", id, e.getMessage());
            }
        }
        return car;
    }
}