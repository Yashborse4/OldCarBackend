package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.car.*;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.CarServiceV2;
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
public class CarControllerV2 {

    private final CarServiceV2 carServiceV2;

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
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        Page<CarResponseV2> cars = carServiceV2.getAllVehicles(pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Vehicles retrieved successfully",
                String.format("Retrieved %d vehicles out of %d total", 
                    cars.getNumberOfElements(), cars.getTotalElements()),
                cars
        ));
    }

    /**
     * Get Vehicle by ID
     * GET /api/cars/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CarResponseV2>> getVehicleById(@PathVariable String id) {
        log.info("Getting vehicle by ID: {}", id);

        CarResponseV2 car = carServiceV2.getVehicleById(id);

        return ResponseEntity.ok(ApiResponse.success(
                "Vehicle retrieved successfully",
                "Vehicle details fetched successfully",
                car
        ));
    }

    /**
     * Create Vehicle
     * POST /api/v2/cars
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SELLER', 'DEALER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CarResponseV2>> createVehicle(
            @Valid @RequestBody CarRequest request) {
        
        log.info("Creating new vehicle: {} {}", request.getMake(), request.getModel());
        
        Long currentUserId = SecurityUtils.getCurrentUserId();
        CarResponseV2 createdCar = carServiceV2.createVehicle(request, currentUserId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Vehicle created successfully",
                        "Your vehicle listing has been created and is now active",
                        createdCar
                ));
    }

    /**
     * Update Vehicle
     * PATCH /api/v2/cars/{id}
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER', 'DEALER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CarResponseV2>> updateVehicle(
            @PathVariable String id,
            @Valid @RequestBody CarRequest request) {
        
        log.info("Updating vehicle: {}", id);
        
        Long currentUserId = SecurityUtils.getCurrentUserId();
        CarResponseV2 updatedCar = carServiceV2.updateVehicle(id, request, currentUserId);

        return ResponseEntity.ok(ApiResponse.success(
                "Vehicle updated successfully",
                "Your vehicle listing has been updated",
                updatedCar
        ));
    }

    /**
     * Delete Vehicle
     * DELETE /api/v2/cars/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER', 'DEALER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> deleteVehicle(
            @PathVariable String id,
            @RequestParam(value = "hard", defaultValue = "false") boolean hard) {
        
        log.info("Deleting vehicle: {} (hard: {})", id, hard);
        
        Long currentUserId = SecurityUtils.getCurrentUserId();
        carServiceV2.deleteVehicle(id, currentUserId, hard);

        return ResponseEntity.ok(ApiResponse.success(
                "Vehicle deleted successfully",
                hard ? "Vehicle permanently deleted" : "Vehicle moved to inactive status"
        ));
    }

    /**
     * Update Vehicle Status
     * POST /api/v2/cars/{id}/status
     */
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SELLER', 'DEALER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CarResponseV2>> updateVehicleStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> statusRequest) {
        
        log.info("Updating vehicle status: {} to {}", id, statusRequest.get("status"));
        
        Long currentUserId = SecurityUtils.getCurrentUserId();
        String newStatus = statusRequest.get("status");
        CarResponseV2 updatedCar = carServiceV2.updateVehicleStatus(id, newStatus, currentUserId);

        return ResponseEntity.ok(ApiResponse.success(
                "Vehicle status updated successfully",
                String.format("Vehicle status changed to %s", newStatus),
                updatedCar
        ));
    }

    /**
     * Search Vehicles with Advanced Filters
     * GET /api/v2/cars/search
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<CarResponseV2>>> searchVehicles(
            @RequestParam(value = "make", required = false) String make,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "minYear", required = false) Integer minYear,
            @RequestParam(value = "maxYear", required = false) Integer maxYear,
            @RequestParam(value = "minPrice", required = false) Long minPrice,
            @RequestParam(value = "maxPrice", required = false) Long maxPrice,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "condition", required = false) String condition,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "featured", required = false) Boolean featured,
            @RequestParam(value = "fuelType", required = false) String fuelType,
            @RequestParam(value = "transmission", required = false) String transmission,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort) {

        log.info("Searching vehicles with filters");

        // Parse sort parameter
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        String sortDirection = sortParams.length > 1 ? sortParams[1] : "desc";

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) 
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        // Create search criteria
        CarSearchCriteria searchCriteria = CarSearchCriteria.builder()
                .make(make)
                .model(model)
                .minYear(minYear)
                .maxYear(maxYear)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .location(location)
                .condition(condition)
                .status(status)
                .featured(featured)
                .fuelType(fuelType)
                .transmission(transmission)
                .build();

        Page<CarResponseV2> cars = carServiceV2.searchVehicles(searchCriteria, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Vehicle search completed",
                String.format("Found %d vehicles matching your criteria", cars.getTotalElements()),
                cars
        ));
    }

    /**
     * Get Vehicle Analytics
     * GET /api/v2/cars/{id}/analytics
     */
    @GetMapping("/{id}/analytics")
    @PreAuthorize("hasAnyRole('SELLER', 'DEALER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CarAnalyticsResponse>> getVehicleAnalytics(@PathVariable String id) {
        log.info("Getting analytics for vehicle: {}", id);

        Long currentUserId = SecurityUtils.getCurrentUserId();
        CarAnalyticsResponse analytics = carServiceV2.getVehicleAnalytics(id, currentUserId);

        return ResponseEntity.ok(ApiResponse.success(
                "Vehicle analytics retrieved successfully",
                "Analytics data for your vehicle listing",
                analytics
        ));
    }

    /**
     * Feature/Unfeature Vehicle
     * POST /api/v2/cars/{id}/feature
     */
    @PostMapping("/{id}/feature")
    @PreAuthorize("hasAnyRole('SELLER', 'DEALER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CarResponseV2>> toggleFeatureVehicle(
            @PathVariable String id,
            @RequestParam("featured") boolean featured) {
        
        log.info("Toggling feature status for vehicle: {} to {}", id, featured);
        
        Long currentUserId = SecurityUtils.getCurrentUserId();
        CarResponseV2 updatedCar = carServiceV2.toggleFeatureVehicle(id, featured, currentUserId);

        return ResponseEntity.ok(ApiResponse.success(
                "Vehicle feature status updated",
                featured ? "Vehicle is now featured" : "Vehicle is no longer featured",
                updatedCar
        ));
    }

    /**
     * Track Vehicle View
     * POST /api/v2/cars/{id}/view
     */
    @PostMapping("/{id}/view")
    public ResponseEntity<ApiResponse<Object>> trackVehicleView(@PathVariable String id) {
        log.info("Tracking view for vehicle: {}", id);

        carServiceV2.trackVehicleView(id);

        return ResponseEntity.ok(ApiResponse.success(
                "View tracked successfully",
                "Vehicle view has been recorded"
        ));
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
        carServiceV2.trackVehicleShare(id, platform);

        return ResponseEntity.ok(ApiResponse.success(
                "Share tracked successfully",
                String.format("Vehicle share on %s has been recorded", platform)
        ));
    }

    /**
     * Get Similar Vehicles
     * GET /api/v2/cars/{id}/similar
     */
    @GetMapping("/{id}/similar")
    public ResponseEntity<ApiResponse<List<CarResponseV2>>> getSimilarVehicles(
            @PathVariable String id,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        
        log.info("Getting similar vehicles for: {} (limit: {})", id, limit);

        List<CarResponseV2> similarVehicles = carServiceV2.getSimilarVehicles(id, limit);

        return ResponseEntity.ok(ApiResponse.success(
                "Similar vehicles retrieved successfully",
                String.format("Found %d similar vehicles", similarVehicles.size()),
                similarVehicles
        ));
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
        Page<CarResponseV2> vehicles = carServiceV2.getVehiclesByDealer(dealerId, status, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Dealer vehicles retrieved successfully",
                String.format("Found %d vehicles for dealer", vehicles.getTotalElements()),
                vehicles
        ));
    }
}
