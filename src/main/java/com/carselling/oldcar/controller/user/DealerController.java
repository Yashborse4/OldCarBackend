package com.carselling.oldcar.controller.user;

import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.dto.car.DealerAnalyticsResponse;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.mapper.CarMapper;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.car.InventoryService;
import com.carselling.oldcar.service.analytics.UserAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for Dealer-specific operations.
 */
@RestController
@RequestMapping("/api/dealer")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dealer Management", description = "Inventory and dashboard for dealers")
public class DealerController {

    private final InventoryService inventoryService;
    private final UserAnalyticsService userAnalyticsService;
    private final CarMapper carMapper;

    /**
     * Get dealer inventory with filters
     */
    @GetMapping("/inventory")
    @PreAuthorize("hasRole('DEALER') or hasRole('ADMIN')")
    @Operation(summary = "Get inventory", description = "Get dealer's car inventory (Active, Sold, Inactive)")
    public ResponseEntity<ApiResponse<Page<CarResponse>>> getInventory(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        Page<Car> inventory = inventoryService.getInventory(user.getId(), status, pageable);

        Page<CarResponse> response = inventory.map(carMapper::toResponse);

        return ResponseEntity.ok(ApiResponse.success("Inventory retrieved", response));
    }

    /**
     * Update car status
     */
    @PatchMapping("/cars/{carId}/status")
    @PreAuthorize("hasRole('DEALER') or hasRole('ADMIN')")
    @Operation(summary = "Update car status", description = "Change status keys (PUBLISHED, SOLD, ARCHIVED)")
    public ResponseEntity<ApiResponse<Void>> updateCarStatus(
            @PathVariable Long carId,
            @RequestBody Map<String, String> statusMap,
            Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        String newStatus = statusMap.get("status");

        if (newStatus == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Status is required"));
        }

        inventoryService.updateCarStatus(carId, user.getId(), newStatus);

        return ResponseEntity.ok(ApiResponse.success("Car status updated"));
    }

    /**
     * Get dealer dashboard analytics
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('DEALER')")
    @Operation(summary = "Dealer Dashboard", description = "Get analytics overview for dealer dashboard")
    public ResponseEntity<ApiResponse<DealerAnalyticsResponse>> getDashboard(
            Authentication authentication) {

        User user = (User) authentication.getPrincipal();
        DealerAnalyticsResponse stats = userAnalyticsService.getDealerAnalytics(user.getId());

        return ResponseEntity.ok(ApiResponse.success("Dashboard stats retrieved", stats));
    }
}
