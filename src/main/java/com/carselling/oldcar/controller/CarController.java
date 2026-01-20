package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.CarStatistics;
import com.carselling.oldcar.dto.car.CarRequest;
import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.dto.car.PublicCarDTO;
import com.carselling.oldcar.dto.car.UpdateCarStatusRequest;
import com.carselling.oldcar.dto.car.TrackCarShareRequest;
import com.carselling.oldcar.dto.car.CarAnalyticsResponse;
import com.carselling.oldcar.dto.car.*;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.CarService;
import com.carselling.oldcar.service.UserAnalyticsService;
import com.carselling.oldcar.util.PageableUtils;
import com.carselling.oldcar.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Enhanced Car Controller V2 - Aligned with API Requirements
 * Handles vehicle management with analytics, co-listing, and advanced features
 */
@RestController
@RequestMapping("/api/cars")
@RequiredArgsConstructor
@Slf4j
public class CarController {

        private final CarService carService;
        private final com.carselling.oldcar.service.CarInteractionEventService carInteractionEventService;
        private final UserAnalyticsService userAnalyticsService;

        /**
         * Get All Vehicles with Enhanced Features
         * GET /api/cars
         */
        @GetMapping
        public ResponseEntity<ApiResponse<Page<CarResponse>>> getAllVehicles(
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort) {

                log.debug("Getting all vehicles - page: {}, size: {}, sort: {}", page, size, sort);

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
        public ResponseEntity<ApiResponse<Page<PublicCarDTO>>> getPublicVehicles(
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort) {

                log.debug("Getting public vehicles - page: {}, size: {}", page, size);
                Pageable pageable = PageableUtils.createPageable(page, size, sort);
                Page<PublicCarDTO> cars = carService.getPublicVehicles(pageable);

                return ResponseEntity.ok(ApiResponse.success(
                                "Public vehicles retrieved",
                                "List of vehicles with public information",
                                cars));
        }

        /**
         * Get Public Vehicle by ID
         * GET /api/cars/public/{id}
         */
        @GetMapping("/public/{id}")
        public ResponseEntity<ApiResponse<PublicCarDTO>> getPublicVehicleById(@PathVariable String id) {
                log.debug("Getting public vehicle by ID: {}", id);
                PublicCarDTO car = carService.getPublicVehicleById(id);
                return ResponseEntity.ok(ApiResponse.success("Vehicle retrieved", "Public vehicle details", car));
        }

        /**
         * Get Vehicle by ID
         * GET /api/cars/{id}
         */
        @GetMapping("/{id}")
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
        public ResponseEntity<ApiResponse<CarResponse>> createVehicle(
                        @Valid @RequestBody CarRequest request,
                        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

                log.info("Creating new vehicle: {} {} (idempotency key: {})",
                                request.getMake(), request.getModel(), idempotencyKey);

                Long currentUserId = SecurityUtils.getCurrentUserId();

                // Check for duplicate request using idempotency key
                if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                        CarResponse existingCar = carService.findByIdempotencyKey(idempotencyKey, currentUserId);
                        if (existingCar != null) {
                                log.info("Returning existing car for idempotency key: {}", idempotencyKey);
                                return ResponseEntity.ok(ApiResponse.success(
                                                "Vehicle already exists",
                                                "Returning previously created vehicle for this request",
                                                existingCar));
                        }
                }

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
        public ResponseEntity<ApiResponse<Object>> deleteVehicle(
                        @PathVariable String id,
                        @RequestParam(value = "hard", defaultValue = "false") boolean hard) {

                log.info("Deleting vehicle: {} (hard: {})", id, hard);

                Long currentUserId = SecurityUtils.getCurrentUserId();
                carService.deleteVehicle(id, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle deleted successfully",
                                "Vehicle permanently deleted")); // Updated message
        }

        /**
         * Update Vehicle Status
         * POST /api/cars/{id}/status
         */
        @PostMapping("/{id}/status")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        public ResponseEntity<ApiResponse<CarResponse>> updateVehicleStatus(
                        @PathVariable String id,
                        @Valid @RequestBody UpdateCarStatusRequest statusRequest) {

                log.info("Updating vehicle status: {} to {}", id, statusRequest.getStatus());

                Long currentUserId = SecurityUtils.getCurrentUserId();
                String newStatus = statusRequest.getStatus();
                CarResponse updatedCar = carService.updateVehicleStatus(id, newStatus, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle status updated successfully",
                                String.format("Vehicle status changed to %s", newStatus),
                                updatedCar));
        }

        /**
         * Update Vehicle Media Status
         * POST /api/cars/{id}/media-status
         */
        @PostMapping("/{id}/media-status")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        public ResponseEntity<ApiResponse<CarResponse>> updateVehicleMediaStatus(
                        @PathVariable String id,
                        @Valid @RequestBody UpdateMediaStatusRequest statusRequest) {

                log.info("Updating vehicle media status: {} to {}", id, statusRequest.getStatus());

                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponse updatedCar = carService.updateMediaStatus(id, statusRequest.getStatus(), currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle media status updated successfully",
                                String.format("Vehicle media status changed to %s", statusRequest.getStatus()),
                                updatedCar));
        }

        /**
         * Toggle Vehicle Visibility (Hide/Show)
         * PATCH /api/cars/{id}/visibility
         */
        @PatchMapping("/{id}/visibility")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        public ResponseEntity<ApiResponse<CarResponse>> toggleVisibility(
                        @PathVariable String id,
                        @RequestParam("visible") boolean visible) {

                log.info("Toggling vehicle visibility: {} to {}", id, visible);

                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponse updatedCar = carService.toggleVisibility(id, visible, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle visibility updated successfully",
                                visible ? "Vehicle is now visible" : "Vehicle is now hidden",
                                updatedCar));
        }

        /**
         * Feature/Unfeature Vehicle
         * POST /api/cars/{id}/feature
         */
        @PostMapping("/{id}/feature")
        @PreAuthorize("hasAnyRole('DEALER', 'ADMIN')")
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
         * Track Vehicle View
         * POST /api/cars/{id}/view
         */
        @PostMapping("/{id}/view")
        public ResponseEntity<ApiResponse<Object>> trackVehicleView(@PathVariable String id) {
                log.info("Tracking view for vehicle: {}", id);

                carService.trackVehicleView(id);

                return ResponseEntity.ok(ApiResponse.success(
                                "View tracked successfully",
                                "Vehicle view has been recorded"));
        }

        /**
         * Track Vehicle Share
         * POST /api/cars/{id}/share
         */
        @PostMapping("/{id}/share")
        public ResponseEntity<ApiResponse<Object>> trackVehicleShare(
                        @PathVariable String id,
                        @Valid @RequestBody TrackCarShareRequest shareRequest) {

                log.info("Tracking share for vehicle: {} on platform: {}", id, shareRequest.getPlatform());

                String platform = shareRequest.getPlatform();
                carService.trackVehicleShare(id, platform);

                return ResponseEntity.ok(ApiResponse.success(
                                "Share tracked successfully",
                                String.format("Vehicle share on %s has been recorded", platform)));
        }

        /**
         * Track any car interaction event
         * POST /api/cars/events
         * 
         * Event types: CAR_VIEW, CONTACT_CLICK, SAVE, SHARE, CHAT_OPEN, etc.
         */
        @PostMapping("/events")
        public ResponseEntity<ApiResponse<Object>> trackCarEvent(
                        @Valid @RequestBody com.carselling.oldcar.dto.car.CarInteractionEventDto eventDto,
                        org.springframework.security.core.Authentication authentication,
                        jakarta.servlet.http.HttpServletRequest request) {

                log.info("Tracking {} event for car {}", eventDto.getEventType(), eventDto.getCarId());

                com.carselling.oldcar.model.CarInteractionEvent.EventType eventType = eventDto.getEventTypeEnum();
                if (eventType == null) {
                        return ResponseEntity.badRequest().body(ApiResponse.error(
                                        "Invalid event type",
                                        "Valid types: CAR_VIEW, CONTACT_CLICK, SAVE, SHARE, CHAT_OPEN"));
                }

                Long userId = null;
                if (authentication != null
                                && authentication.getPrincipal() instanceof com.carselling.oldcar.model.User) {
                        userId = ((com.carselling.oldcar.model.User) authentication.getPrincipal()).getId();
                }

                String ipAddress = request.getRemoteAddr();
                String userAgent = request.getHeader("User-Agent");

                carInteractionEventService.trackEvent(
                                eventDto.getCarId(),
                                userId,
                                eventType,
                                eventDto.getSessionId(),
                                userAgent,
                                ipAddress,
                                eventDto.getReferrer(),
                                eventDto.getMetadata());

                return ResponseEntity.ok(ApiResponse.success(
                                "Event tracked",
                                eventType.getDisplayName() + " event recorded"));
        }

        /**
         * Get event statistics for a car
         * GET /api/cars/{id}/events/stats
         */
        @GetMapping("/{id}/events/stats")
        @PreAuthorize("hasRole('DEALER') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> getCarEventStats(@PathVariable String id) {
                log.info("Getting event stats for car {}", id);

                Long carId = Long.parseLong(id);
                java.util.Map<String, Long> stats = carInteractionEventService.getCarEventStats(carId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Event stats retrieved",
                                "Statistics for car " + id,
                                stats));
        }

        /**
         * Get Vehicles by Dealer (public)
         * GET /api/cars/seller/{dealerId}/cars
         */
        @GetMapping("/seller/{dealerId}")
        public ResponseEntity<ApiResponse<Page<CarResponse>>> getVehiclesByDealer(
                        @PathVariable String dealerId,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        @RequestParam(value = "status", required = false) String status) {

                log.info("Getting vehicles by dealer: {} (page: {}, size: {})", dealerId, page, size);

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
        public ResponseEntity<ApiResponse<com.carselling.oldcar.dto.car.DealerDashboardResponse>> getDealerDashboard() {
                Long currentUserId = SecurityUtils.getCurrentUserId();
                com.carselling.oldcar.dto.car.DealerDashboardResponse stats = userAnalyticsService
                                .getDealerDashboardStats(currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Dealer dashboard statistics retrieved successfully",
                                "Aggregated metrics for your vehicle listings",
                                stats));
        }

        @GetMapping("/dealer/my-cars")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
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
        public ResponseEntity<ApiResponse<Void>> incrementCarStat(
                        @PathVariable String id,
                        @RequestParam String type) {

                carService.incrementCarStat(id, type);
                return ResponseEntity.ok(ApiResponse.success("Stat incremented", null, null));
        }
}
