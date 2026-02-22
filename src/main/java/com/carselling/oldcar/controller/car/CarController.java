package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.CarStatistics;
import com.carselling.oldcar.dto.car.CarRequest;
import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.dto.car.PublicCarDTO;
import com.carselling.oldcar.dto.car.CarAnalyticsResponse;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.car.CarService;
import com.carselling.oldcar.service.analytics.UserAnalyticsService;
import com.carselling.oldcar.util.PageableUtils;
import com.carselling.oldcar.util.SecurityUtils;
import com.carselling.oldcar.b2.B2FileService;
import com.carselling.oldcar.model.MediaStatus;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.carselling.oldcar.dto.file.FileUploadResponse;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import com.carselling.oldcar.dto.validation.OnCreate;

import java.util.concurrent.TimeUnit;

/**
 * Enhanced Car Controller V2 - Aligned with API Requirements
 * Handles vehicle management with analytics, co-listing, and advanced features
 */
@RestController
@RequestMapping("/api/cars")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Car Management", description = "Endpoints for managing vehicles")
public class CarController {

        private final CarService carService;
        private final UserAnalyticsService userAnalyticsService;
        private final B2FileService b2FileService;

        /**
         * Get All Vehicles with Enhanced Features
         * GET /api/cars
         */
        @GetMapping
        @Operation(summary = "Get all vehicles", description = "Retrieve a paginated list of all vehicles accessible to the current user")
        public ResponseEntity<ApiResponse<Page<CarResponse>>> getAllVehicles(
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort) {

                log.debug("Getting all vehicles - page: {}, size: {}, sort: {}", page, size, sort);

                Pageable pageable = PageableUtils.createPageable(page, size, sort);

                Page<CarResponse> cars = carService.getAllVehicles(pageable);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicles retrieved successfully",
                                String.format("Retrieved %d vehicles out of %d total",
                                                cars.getNumberOfElements(), cars.getTotalElements()),
                                cars));
        }

        /**
         * Get Public Vehicles (Limited Data)
         * GET /api/cars/public
         */
        @GetMapping("/public")
        @Operation(summary = "Get public vehicles", description = "Retrieve a paginated list of public vehicles (limited data)")
        public ResponseEntity<ApiResponse<Page<PublicCarDTO>>> getPublicVehicles(
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort) {

                log.debug("Getting public vehicles - page: {}, size: {}", page, size);
                Pageable pageable = PageableUtils.createPageable(page, size, sort);
                Page<PublicCarDTO> cars = carService.getPublicVehicles(pageable);

                return ResponseEntity.ok()
                                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                                .body(ApiResponse.success(
                                                "Public vehicles retrieved",
                                                "List of vehicles with public information",
                                                cars));
        }

        /**
         * Get Public Vehicle by ID
         * GET /api/cars/public/{id}
         */
        @GetMapping("/public/{id}")
        @Operation(summary = "Get public vehicle details", description = "Retrieve public details of a specific vehicle")
        public ResponseEntity<ApiResponse<PublicCarDTO>> getPublicVehicleById(@PathVariable String id) {
                log.debug("Getting public vehicle by ID: {}", id);
                PublicCarDTO car = carService.getPublicVehicleById(id);
                return ResponseEntity.ok()
                                .cacheControl(CacheControl.maxAge(120, TimeUnit.SECONDS).cachePublic())
                                .body(ApiResponse.success("Vehicle retrieved", "Public vehicle details", car));
        }

        /**
         * Get Vehicle by ID
         * GET /api/cars/{id}
         */
        @GetMapping("/{id}")
        @Operation(summary = "Get vehicle details", description = "Retrieve full details of a specific vehicle")
        public ResponseEntity<ApiResponse<CarResponse>> getVehicleById(@PathVariable String id) {
                log.debug("Getting vehicle by ID: {}", id);

                CarResponse car = carService.getVehicleById(id);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle retrieved successfully",
                                "Vehicle details fetched successfully",
                                car));
        }

        @GetMapping("/{id}/analytics")
        @PreAuthorize("hasAnyRole('DEALER', 'ADMIN')")
        @Operation(summary = "Get vehicle analytics", description = "Retrieve analytics data for a specific vehicle (Dealer/Admin only)")
        public ResponseEntity<ApiResponse<CarAnalyticsResponse>> getVehicleAnalytics(@PathVariable String id) {
                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarAnalyticsResponse analytics = carService.getVehicleAnalytics(id, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle analytics retrieved successfully",
                                "Analytics for this vehicle",
                                analytics));
        }

        /**
         * Create Vehicle
         * POST /api/cars
         *
         * Permission matrix:
         * - USER: can create own cars
         * - DEALER: can create cars
         * - ADMIN: can create cars for anyone
         * 
         * Supports idempotency key via X-Idempotency-Key header to prevent duplicates
         * on retries.
         */
        @PostMapping
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @Operation(summary = "Create a new vehicle", description = "Create a new vehicle listing")
        public ResponseEntity<ApiResponse<CarResponse>> createVehicle(
                        @Validated(OnCreate.class) @RequestBody CarRequest request,
                        @Parameter(description = "Idempotency key to prevent duplicate requests") @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

                log.info("Creating new vehicle: {} {} (idempotency key: {})",
                                request.getMake(), request.getModel(), idempotencyKey);

                Long currentUserId = SecurityUtils.getCurrentUserId();

                CarResponse createdCar = carService.createVehicle(request, currentUserId, idempotencyKey);

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(
                                                "Vehicle created successfully",
                                                "Your vehicle listing has been created and is now active",
                                                createdCar));
        }

        /**
         * Update Vehicle
         * PATCH /api/cars/{id}
         *
         * Permission matrix:
         * - USER: own cars only (enforced in service via ownership check)
         * - DEALER: own cars only
         * - ADMIN: any car
         */
        @PatchMapping("/{id}")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @Operation(summary = "Update vehicle", description = "Update details of an existing vehicle")
        public ResponseEntity<ApiResponse<CarResponse>> updateVehicle(
                        @PathVariable String id,
                        @Valid @RequestBody CarRequest request) {

                log.info("Updating vehicle: {}", id);

                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponse updatedCar = carService.updateVehicle(id, request, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle updated successfully",
                                "Your vehicle listing has been updated",
                                updatedCar));
        }

        /**
         * Delete Vehicle
         * DELETE /api/cars/{id}
         *
         * Permission matrix:
         * - USER/DEALER: can delete own cars (service enforces ownership)
         * - ADMIN: can delete any car ("delete any car" action)
         */
        @DeleteMapping("/{id}")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @Operation(summary = "Delete vehicle", description = "Delete a vehicle listing")
        public ResponseEntity<ApiResponse<Object>> deleteVehicle(
                        @PathVariable String id,
                        @RequestParam(value = "hard", defaultValue = "false") boolean hard) {

                log.info("Deleting vehicle: {} (hard: {})", id, hard);

                Long currentUserId = SecurityUtils.getCurrentUserId();
                // Ownership check is enforced by Service

                carService.deleteVehicle(id, currentUserId, hard);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle deleted successfully",
                                hard ? "Vehicle permanently deleted" : "Vehicle deleted"));
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        public ResponseEntity<ApiResponse<CarResponse>> updateCar(
                @PathVariable String id,
                @Valid @RequestPart("car") CarRequest carRequest,
                @RequestPart(value = "images", required = false) List<MultipartFile> images) {

                Long currentUserId = SecurityUtils.getCurrentUserId();

                CarResponse updatedCar = carService.updateVehicleWithMedia(id, carRequest, images, currentUserId);

                return ResponseEntity.ok(ApiResponse.success("Vehicle updated successfully", updatedCar));
        }

        @PatchMapping("/{id}/status")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @Operation(summary = "Update vehicle status", description = "Change the status of a vehicle")
        public ResponseEntity<ApiResponse<CarResponse>> updateCarStatus(
                        @PathVariable String id,
                        @RequestParam String status) {

                Long currentUserId = SecurityUtils.getCurrentUserId();
                // Ownership check is enforced by Service

                return ResponseEntity.ok(ApiResponse.success("Vehicle status updated successfully",
                                carService.updateVehicleStatus(id, status, currentUserId)));
        }

        @PatchMapping("/{id}/visibility")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @Operation(summary = "Toggle vehicle visibility", description = "Show or hide a vehicle")
        public ResponseEntity<ApiResponse<CarResponse>> toggleVisibility(
                        @PathVariable String id,
                        @RequestParam boolean visible) {

                Long currentUserId = SecurityUtils.getCurrentUserId();
                // Ownership check is enforced by Service

                return ResponseEntity.ok(ApiResponse.success("Vehicle visibility updated successfully",
                                carService.toggleVisibility(id, visible, currentUserId)));
        }

        @PatchMapping("/{id}/media/status")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @Operation(summary = "Update media status", description = "Update the processing status of vehicle media")
        public ResponseEntity<ApiResponse<CarResponse>> updateMediaStatus(
                        @PathVariable String id,
                        @RequestParam MediaStatus status) {

                Long currentUserId = SecurityUtils.getCurrentUserId();
                // Ownership check is enforced by Service

                return ResponseEntity.ok(ApiResponse.success("Media status updated successfully",
                                carService.updateMediaStatus(id, status, currentUserId)));
        }

        // NOTE: uploadMedia endpoint moved to CarMediaController.uploadVehicleMedia
        // to avoid duplicate endpoint conflict - POST /api/cars/{id}/media

        /**
         * Feature/Unfeature Vehicle
         * POST /api/cars/{id}/feature
         */
        @PostMapping("/{id}/feature")
        @PreAuthorize("hasAnyRole('DEALER', 'ADMIN')")
        @Operation(summary = "Toggle feature status", description = "Feature or unfeature a vehicle (Dealer/Admin only)")
        public ResponseEntity<ApiResponse<CarResponse>> toggleFeatureVehicle(
                        @PathVariable String id,
                        @RequestParam("featured") boolean featured) {

                log.info("Toggling feature status for vehicle: {} to {}", id, featured);

                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponse updatedCar = carService.toggleFeatureVehicle(id, featured, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle feature status updated",
                                featured ? "Vehicle is now featured" : "Vehicle is no longer featured",
                                updatedCar));
        }

        /**
         * Get Vehicles by Dealer (public)
         * GET /api/cars/seller/{dealerId}/cars
         */
        @GetMapping("/seller/{dealerId}")
        @Operation(summary = "Get dealer's vehicles", description = "Retrieve vehicles listed by a specific dealer")
        public ResponseEntity<ApiResponse<Page<CarResponse>>> getVehiclesByDealer(
                        @PathVariable String dealerId,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        @RequestParam(value = "status", required = false) String status) {

                log.info("Getting vehicles by dealer: {} (page: {}, size: {})", dealerId, page, size);

                Pageable pageable = PageableUtils.createPageable(page, size);
                Page<CarResponse> vehicles = carService.getVehiclesByDealer(dealerId, status, pageable);

                return ResponseEntity.ok(ApiResponse.success(
                                "Dealer vehicles retrieved successfully",
                                String.format("Found %d vehicles for dealer", vehicles.getTotalElements()),
                                vehicles));
        }

        @GetMapping("/dealer/dashboard")
        @PreAuthorize("hasAnyRole('DEALER', 'ADMIN')")
        @Operation(summary = "Get dealer dashboard", description = "Retrieve dashboard statistics for the current dealer")
        public ResponseEntity<ApiResponse<com.carselling.oldcar.dto.car.DealerDashboardResponse>> getDealerDashboard() {
                Long currentUserId = SecurityUtils.getCurrentUserId();
                com.carselling.oldcar.dto.car.DealerDashboardResponse stats = userAnalyticsService
                                .getDealerDashboardStats(currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Dealer dashboard statistics retrieved successfully",
                                "Aggregated metrics for your vehicle listings",
                                stats));
        }

        @GetMapping("/dealer/analytics")
        @PreAuthorize("hasAnyRole('DEALER', 'ADMIN')")
        @Operation(summary = "Get dealer analytics", description = "Retrieve detailed analytics for the current dealer")
        public ResponseEntity<ApiResponse<com.carselling.oldcar.dto.car.DealerAnalyticsResponse>> getDealerAnalytics() {
                Long currentUserId = SecurityUtils.getCurrentUserId();
                com.carselling.oldcar.dto.car.DealerAnalyticsResponse analytics = userAnalyticsService
                                .getDealerAnalytics(currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Dealer analytics retrieved successfully",
                                "Detailed analytics for your account",
                                analytics));
        }

        @GetMapping("/dealer/my-cars")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @Operation(summary = "Get my cars", description = "Retrieve vehicles listed by the current user")
        public ResponseEntity<ApiResponse<Page<CarResponse>>> getDealerCars(
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        @RequestParam(value = "status", required = false) String status) {

                Long currentUserId = SecurityUtils.getCurrentUserId();
                Pageable pageable = PageableUtils.createPageable(page, size);
                Page<CarResponse> vehicles = carService.getVehiclesByDealer(currentUserId.toString(), status,
                                pageable);

                return ResponseEntity.ok(ApiResponse.success(
                                "Dealer vehicles retrieved successfully",
                                String.format("Found %d vehicles for dealer", vehicles.getTotalElements()),
                                vehicles));
        }

        @GetMapping("/admin/analytics")
        @PreAuthorize("hasRole('ADMIN')")
        @Operation(summary = "Get admin analytics", description = "Retrieve system-wide car statistics (Admin only)")
        public ResponseEntity<ApiResponse<CarStatistics>> getAdminCarAnalytics() {
                CarStatistics statistics = carService.getCarStatistics();

                return ResponseEntity.ok(ApiResponse.success(
                                "Car statistics retrieved successfully",
                                "Aggregated car statistics for admin",
                                statistics));
        }

        /**
         * Increment Car Statistics
         * POST /api/cars/{id}/stats?type={type}
         */
        @PostMapping("/{id}/stats")
        @Operation(summary = "Increment car statistic", description = "Increment a specific statistic for a car (e.g., view, share)")
        public ResponseEntity<ApiResponse<Void>> incrementCarStat(
                        @PathVariable String id,
                        @RequestParam String type) {

                carService.incrementCarStat(id, type);
                return ResponseEntity.ok(ApiResponse.success("Stat incremented", null, null));
        }
}
