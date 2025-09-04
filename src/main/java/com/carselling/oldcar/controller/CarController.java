package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.car.CarRequest;
import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.service.CarService;
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

import java.math.BigDecimal;

/**
 * Car Controller for car management operations
 * Handles car CRUD operations, search, filtering, and car-related endpoints
 */
@RestController
@RequestMapping("/api/cars")
@RequiredArgsConstructor
@Slf4j
public class CarController {

    private final CarService carService;

    /**
     * Get all active cars (public endpoint)
     * GET /api/cars
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CarResponse>>> getAllActiveCars(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt") String sort,
            @RequestParam(value = "direction", defaultValue = "desc") String direction) {
        
        log.info("Getting all active cars");

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) 
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<CarResponse> cars = carService.getAllActiveCars(pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Cars retrieved successfully",
                String.format("Retrieved %d active cars.", cars.getTotalElements()),
                cars
        ));
    }

    /**
     * Get car by ID (public endpoint)
     * GET /api/cars/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CarResponse>> getCarById(@PathVariable Long id) {
        log.info("Getting car by ID: {}", id);

        CarResponse car = carService.getCarById(id);

        return ResponseEntity.ok(ApiResponse.success(
                "Car retrieved successfully",
                "Car details have been retrieved.",
                car
        ));
    }

    /**
     * Search cars with advanced filters (public endpoint)
     * GET /api/cars/search
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<CarResponse>>> searchCars(
            @RequestParam(value = "make", required = false) String make,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "minYear", required = false) Integer minYear,
            @RequestParam(value = "maxYear", required = false) Integer maxYear,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "ownerRole", required = false) String ownerRoleString,
            @RequestParam(value = "isFeatured", required = false) Boolean isFeatured,
            @RequestParam(value = "isSold", required = false) Boolean isSold,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt") String sort,
            @RequestParam(value = "direction", defaultValue = "desc") String direction) {
        
        log.info("Searching cars with filters");

        Role ownerRole = null;
        if (ownerRoleString != null) {
            try {
                ownerRole = Role.valueOf(ownerRoleString.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(
                                "Invalid owner role parameter",
                                "Valid roles are: VIEWER, SELLER, DEALER, ADMIN"
                        ));
            }
        }

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) 
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<CarResponse> cars = carService.searchCars(
                make, model, minYear, maxYear, minPrice, maxPrice,
                ownerRole, isFeatured, isSold, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Car search completed",
                String.format("Found %d cars matching the search criteria.", cars.getTotalElements()),
                cars
        ));
    }

    /**
     * Full text search across cars (public endpoint)
     * GET /api/cars/text-search
     */
    @GetMapping("/text-search")
    public ResponseEntity<ApiResponse<Page<CarResponse>>> searchCarsByText(
            @RequestParam(value = "q") String searchTerm,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt") String sort,
            @RequestParam(value = "direction", defaultValue = "desc") String direction) {
        
        log.info("Full text search for cars with term: {}", searchTerm);

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) 
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<CarResponse> cars = carService.searchCarsByText(searchTerm, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Text search completed",
                String.format("Found %d cars matching '%s'.", cars.getTotalElements(), searchTerm),
                cars
        ));
    }

    /**
     * Get featured cars (public endpoint)
     * GET /api/cars/featured
     */
    @GetMapping("/featured")
    public ResponseEntity<ApiResponse<Page<CarResponse>>> getFeaturedCars(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "featuredUntil") String sort,
            @RequestParam(value = "direction", defaultValue = "desc") String direction) {
        
        log.info("Getting featured cars");

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) 
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<CarResponse> cars = carService.getFeaturedCars(pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Featured cars retrieved successfully",
                String.format("Retrieved %d featured cars.", cars.getTotalElements()),
                cars
        ));
    }

    /**
     * Get recent cars (public endpoint)
     * GET /api/cars/recent
     */
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<Page<CarResponse>>> getRecentCars(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        
        log.info("Getting recent cars");

        Pageable pageable = PageRequest.of(page, size);
        Page<CarResponse> cars = carService.getRecentCars(pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Recent cars retrieved successfully",
                String.format("Retrieved %d recent cars.", cars.getTotalElements()),
                cars
        ));
    }

    /**
     * Get most viewed cars (public endpoint)
     * GET /api/cars/popular
     */
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<Page<CarResponse>>> getMostViewedCars(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        
        log.info("Getting most viewed cars");

        Pageable pageable = PageRequest.of(page, size);
        Page<CarResponse> cars = carService.getMostViewedCars(pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Popular cars retrieved successfully",
                String.format("Retrieved %d popular cars.", cars.getTotalElements()),
                cars
        ));
    }

    /**
     * Create a new car (requires SELLER/DEALER role)
     * POST /api/cars
     */
    @PostMapping
    @PreAuthorize("hasRole('SELLER') or hasRole('DEALER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CarResponse>> createCar(
            @Valid @RequestBody CarRequest request) {
        
        log.info("Creating new car");

        CarResponse car = carService.createCar(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Car created successfully",
                        "Your car has been listed successfully.",
                        car
                ));
    }

    /**
     * Update car (owner or admin only)
     * PUT /api/cars/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SELLER') or hasRole('DEALER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CarResponse>> updateCar(
            @PathVariable Long id,
            @Valid @RequestBody CarRequest request) {
        
        log.info("Updating car with ID: {}", id);

        CarResponse car = carService.updateCar(id, request);

        return ResponseEntity.ok(ApiResponse.success(
                "Car updated successfully",
                "Your car details have been updated.",
                car
        ));
    }

    /**
     * Delete car (owner or admin only)
     * DELETE /api/cars/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SELLER') or hasRole('DEALER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> deleteCar(
            @PathVariable Long id,
            @RequestParam(value = "hard", defaultValue = "false") boolean hardDelete) {
        
        log.info("Deleting car with ID: {}, hard delete: {}", id, hardDelete);

        carService.deleteCar(id, hardDelete);

        return ResponseEntity.ok(ApiResponse.success(
                "Car deleted successfully",
                hardDelete ? "Car has been permanently deleted." : "Car has been deactivated."
        ));
    }

    /**
     * Feature/Unfeature car (DEALER/ADMIN only)
     * POST /api/cars/{id}/feature
     */
    @PostMapping("/{id}/feature")
    @PreAuthorize("hasRole('DEALER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CarResponse>> toggleCarFeature(
            @PathVariable Long id,
            @RequestParam(value = "featured", defaultValue = "true") boolean featured,
            @RequestParam(value = "days", defaultValue = "7") int daysToFeature) {
        
        log.info("Toggling feature status for car ID: {}, featured: {}", id, featured);

        CarResponse car = carService.toggleCarFeature(id, featured, daysToFeature);

        return ResponseEntity.ok(ApiResponse.success(
                featured ? "Car featured successfully" : "Car unfeatured successfully",
                featured ? 
                    String.format("Car has been featured for %d days.", daysToFeature) :
                    "Car is no longer featured.",
                car
        ));
    }

    /**
     * Mark car as sold/unsold
     * POST /api/cars/{id}/sold
     */
    @PostMapping("/{id}/sold")
    @PreAuthorize("hasRole('SELLER') or hasRole('DEALER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CarResponse>> markCarAsSold(
            @PathVariable Long id,
            @RequestParam(value = "sold", defaultValue = "true") boolean sold) {
        
        log.info("Marking car ID: {} as sold: {}", id, sold);

        CarResponse car = carService.markCarAsSold(id, sold);

        return ResponseEntity.ok(ApiResponse.success(
                sold ? "Car marked as sold" : "Car marked as available",
                sold ? "Car has been marked as sold." : "Car is now available for sale.",
                car
        ));
    }

    /**
     * Get current user's cars (SELLER/DEALER only)
     * GET /api/cars/mycars
     */
    @GetMapping("/mycars")
    @PreAuthorize("hasRole('SELLER') or hasRole('DEALER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<CarResponse>>> getUserCars(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt") String sort,
            @RequestParam(value = "direction", defaultValue = "desc") String direction) {
        
        log.info("Getting current user's cars");

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) 
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<CarResponse> cars = carService.getUserCars(pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Your cars retrieved successfully",
                String.format("You have %d cars listed.", cars.getTotalElements()),
                cars
        ));
    }

    /**
     * Get cars by owner ID (admin only)
     * GET /api/cars/owner/{ownerId}
     */
    @GetMapping("/owner/{ownerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<CarResponse>>> getCarsByOwnerId(
            @PathVariable Long ownerId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt") String sort,
            @RequestParam(value = "direction", defaultValue = "desc") String direction) {
        
        log.info("Getting cars for owner ID: {}", ownerId);

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) 
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<CarResponse> cars = carService.getCarsByOwnerId(ownerId, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Cars retrieved successfully",
                String.format("Retrieved %d cars for owner ID %d.", cars.getTotalElements(), ownerId),
                cars
        ));
    }

    /**
     * Get car statistics (admin only)
     * GET /api/cars/statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CarService.CarStatistics>> getCarStatistics() {
        log.info("Getting car statistics");

        CarService.CarStatistics statistics = carService.getCarStatistics();

        return ResponseEntity.ok(ApiResponse.success(
                "Car statistics retrieved successfully",
                "Current system car statistics have been retrieved.",
                statistics
        ));
    }
}
