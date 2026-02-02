package com.carselling.oldcar.controller.analytics;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.vehicle.VehicleRecommendationDto;
import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.analytics.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller for Vehicle Recommendations
 */
@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Recommendations", description = "Smart vehicle recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * Get similar cars
     */
    @GetMapping("/similar/{carId}")
    @Operation(summary = "Get similar cars", description = "Get cars similar to specific listing")
    public ResponseEntity<ApiResponse<List<VehicleRecommendationDto>>> getSimilarCars(@PathVariable Long carId) {
        List<VehicleRecommendationDto> recommendations = recommendationService.getSimilarCars(carId);
        return ResponseEntity.ok(ApiResponse.success("Similar cars retrieved", recommendations));
    }

    /**
     * Get personalized recommendations (requires auth)
     */
    @GetMapping("/personalized")
    @Operation(summary = "Get personalized feed", description = "Get recommendations based on user history")
    public ResponseEntity<ApiResponse<List<VehicleRecommendationDto>>> getPersonalizedRecommendations(
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            // Return trending if not auth? Or simple empty. Auth is optional?
            // Usually /personalized implies auth.
            // If we want public trending, maybe another endpoint or handle null here.
            // Let's enforce auth for "personalized".
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required for personalized feed"));
        }

        UserPrincipal user = (UserPrincipal) authentication.getPrincipal(); // Assuming principal is UserPrincipal
        List<VehicleRecommendationDto> recommendations = recommendationService
                .getPersonalizedRecommendations(user.getId());

        return ResponseEntity.ok(ApiResponse.success("Personalized recommendations retrieved", recommendations));
    }
}
