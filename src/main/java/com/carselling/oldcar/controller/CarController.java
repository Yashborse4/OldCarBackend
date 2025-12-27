package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.car.*;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.CarService;
import com.carselling.oldcar.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Enhanced Car Controller V2 - Aligned with API Requirements
 * Handles vehicle management with analytics, co-listing, and advanced features
 */
@RestController
@RequestMapping("/api/v2/cars")
@RequiredArgsConstructor
@Slf4j
public class CarController {

        private final CarService carService;

        /**
         * Get All Vehicles with Enhanced Features
         * GET /api/v2/cars
         */
        /**
         * Get All Vehicles with Enhanced Features
         * GET /api/v2/cars
         */
        @GetMapping
        public ResponseEntity<ApiResponse<Page<CarResponseV2>>> getAllVehicles(
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort) {

                log.info("Getting all vehicles - page: {}, size: {}, sort: {}", page, size, sort);

                // Parse sort parameter
                String[] sortParams = sort.split(",");
                String sortField = sortParams[0];
                String sortDirection = sortParams.length > 1 ? sortParams[1] : "desc";

                Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection)
                                ? Sort.Direction.ASC
                                : Sort.Direction.DESC;
                Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

                Page<CarResponseV2> cars = carService.getAllVehicles(pageable);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicles retrieved successfully",
                                String.format("Retrieved %d vehicles out of %d total",
                                                cars.getNumberOfElements(), cars.getTotalElements()),
                                cars));
        }

        /**
         * Get Public Vehicles (Limited Data)
         * GET /api/v2/cars/public
         */
        @GetMapping("/public")
        public ResponseEntity<ApiResponse<Page<PublicCarDTO>>> getPublicVehicles(
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort) {

                log.info("Getting public vehicles");
                Pageable pageable = PageRequest.of(page, size); // Simplified sort for now
                Page<PublicCarDTO> cars = carService.getPublicVehicles(pageable);

                return ResponseEntity.ok(ApiResponse.success(
                                "Public vehicles retrieved",
                                "List of vehicles with public information",
                                cars));
        }

        /**
         * Get Public Vehicle by ID
         * GET /api/v2/cars/public/{id}
         */
        @GetMapping("/public/{id}")
        public ResponseEntity<ApiResponse<PublicCarDTO>> getPublicVehicleById(@PathVariable String id) {
                log.info("Getting public vehicle by ID: {}", id);
                PublicCarDTO car = carService.getPublicVehicleById(id);
                return ResponseEntity.ok(ApiResponse.success("Vehicle retrieved", "Public vehicle details", car));
        }

        /**
         * Get Vehicle by ID
         * GET /api/cars/{id}
         */
        @GetMapping("/{id}")
        public ResponseEntity<ApiResponse<CarResponseV2>> getVehicleById(@PathVariable String id) {
                log.info("Getting vehicle by ID: {}", id);

                CarResponseV2 car = carService.getVehicleById(id);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle retrieved successfully",
                                "Vehicle details fetched successfully",
                                car));
        }

        /**
         * Create Vehicle
         * POST /api/v2/cars
         *
         * Permission matrix:
         * - USER: can create own cars
         * - DEALER: can create cars
         * - ADMIN: can create cars for anyone
         */
        @PostMapping
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        public ResponseEntity<ApiResponse<CarResponseV2>> createVehicle(
                        @Valid @RequestBody CarRequest request) {

                log.info("Creating new vehicle: {} {}", request.getMake(), request.getModel());

                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponseV2 createdCar = carService.createVehicle(request, currentUserId);

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success(
                                                "Vehicle created successfully",
                                                "Your vehicle listing has been created and is now active",
                                                createdCar));
        }

        /**
         * Update Vehicle
         * PATCH /api/v2/cars/{id}
         *
         * Permission matrix:
         * - USER: own cars only (enforced in service via ownership check)
         * - DEALER: own cars only
         * - ADMIN: any car
         */
        @PatchMapping("/{id}")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        public ResponseEntity<ApiResponse<CarResponseV2>> updateVehicle(
                        @PathVariable String id,
                        @Valid @RequestBody CarRequest request) {

                log.info("Updating vehicle: {}", id);

                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponseV2 updatedCar = carService.updateVehicle(id, request, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle updated successfully",
                                "Your vehicle listing has been updated",
                                updatedCar));
        }

        /**
         * Delete Vehicle
         * DELETE /api/v2/cars/{id}
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
                carService.deleteVehicle(id, currentUserId, hard);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle deleted successfully",
                                hard ? "Vehicle permanently deleted" : "Vehicle moved to inactive status"));
        }

        /**
         * Update Vehicle Status
         * POST /api/v2/cars/{id}/status
         */
        @PostMapping("/{id}/status")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        public ResponseEntity<ApiResponse<CarResponseV2>> updateVehicleStatus(
                        @PathVariable String id,
                        @RequestBody Map<String, String> statusRequest) {

                log.info("Updating vehicle status: {} to {}", id, statusRequest.get("status"));

                Long currentUserId = SecurityUtils.getCurrentUserId();
                String newStatus = statusRequest.get("status");
                CarResponseV2 updatedCar = carService.updateVehicleStatus(id, newStatus, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle status updated successfully",
                                String.format("Vehicle status changed to %s", newStatus),
                                updatedCar));
        }

        /**
         * Toggle Vehicle Visibility (Hide/Show)
         * PATCH /api/v2/cars/{id}/visibility
         */
        @PatchMapping("/{id}/visibility")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        public ResponseEntity<ApiResponse<CarResponseV2>> toggleVisibility(
                        @PathVariable String id,
                        @RequestParam("visible") boolean visible) {

                log.info("Toggling vehicle visibility: {} to {}", id, visible);

                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponseV2 updatedCar = carService.toggleVisibility(id, visible, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle visibility updated successfully",
                                visible ? "Vehicle is now visible" : "Vehicle is now hidden",
                                updatedCar));
        }

        /**
         * Feature/Unfeature Vehicle
         * POST /api/v2/cars/{id}/feature
         */
        @PostMapping("/{id}/feature")
        @PreAuthorize("hasAnyRole('DEALER', 'ADMIN')")
        public ResponseEntity<ApiResponse<CarResponseV2>> toggleFeatureVehicle(
                        @PathVariable String id,
                        @RequestParam("featured") boolean featured) {

                log.info("Toggling feature status for vehicle: {} to {}", id, featured);

                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponseV2 updatedCar = carService.toggleFeatureVehicle(id, featured, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle feature status updated",
                                featured ? "Vehicle is now featured" : "Vehicle is no longer featured",
                                updatedCar));
        }

        /**
         * Track Vehicle View
         * POST /api/v2/cars/{id}/view
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
         * POST /api/v2/cars/{id}/share
         */
        @PostMapping("/{id}/share")
        public ResponseEntity<ApiResponse<Object>> trackVehicleShare(
                        @PathVariable String id,
                        @RequestBody Map<String, String> shareRequest) {

                log.info("Tracking share for vehicle: {} on platform: {}", id, shareRequest.get("platform"));

                String platform = shareRequest.get("platform");
                carService.trackVehicleShare(id, platform);

                return ResponseEntity.ok(ApiResponse.success(
                                "Share tracked successfully",
                                String.format("Vehicle share on %s has been recorded", platform)));
        }

        /**
         * Get Vehicles by Dealer
         * GET /api/v1/seller/{dealerId}/cars
         */
        @GetMapping("/seller/{dealerId}")
        public ResponseEntity<ApiResponse<Page<CarResponseV2>>> getVehiclesByDealer(
                        @PathVariable String dealerId,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        @RequestParam(value = "status", required = false) String status) {

                log.info("Getting vehicles by dealer: {} (page: {}, size: {})", dealerId, page, size);

                Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
                Page<CarResponseV2> vehicles = carService.getVehiclesByDealer(dealerId, status, pageable);

                return ResponseEntity.ok(ApiResponse.success(
                                "Dealer vehicles retrieved successfully",
                                String.format("Found %d vehicles for dealer", vehicles.getTotalElements()),
                                vehicles));
        }
}
