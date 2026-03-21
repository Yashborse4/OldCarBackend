package com.carselling.oldcar.controller.analytics;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.user.UserPreferenceDto;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ResponseEntity<ApiResponse<List<VehicleRecommendationDto>>> getSimilarCars(
            @PathVariable Long carId,
            @RequestParam(value = "city", required = false) String city) {
        List<VehicleRecommendationDto> recommendations = recommendationService.getSimilarCars(carId, city);
        return ResponseEntity.ok(ApiResponse.success("Similar cars retrieved", recommendations));
    }

    /**
     * Get personalized recommendations (requires auth)
     */
    @PostMapping("/personalized")
    @Operation(summary = "Get personalized feed", description = "Get recommendations based on frontend preferences")
    public ResponseEntity<ApiResponse<List<VehicleRecommendationDto>>> getPersonalizedRecommendations(
            Authentication authentication,
            @RequestBody UserPreferenceDto userPreferences,
            @RequestParam(value = "city", required = false) String city) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(ApiResponse.error("Authentication required for personalized feed"));
        }

        List<VehicleRecommendationDto> recommendations = recommendationService
                .getRecommendationsBasedOnPreferences(userPreferences, city);

        return ResponseEntity.ok(ApiResponse.success("Personalized recommendations retrieved", recommendations));
    }

    /**
     * Get Guest Recommendations (no auth required)
     * POST /api/recommendations/guest
     */
    @PostMapping("/guest")
    @Operation(summary = "Get guest feed", description = "Get recommendations based on explicit guest preferences")
    // TODO(SeniorEng): Security - Guest endpoint lacks rate limiting. Add
    // @RateLimit or Bot protection to prevent abuse by scrapers fetching raw
    // recommendation data.
    public ResponseEntity<ApiResponse<List<VehicleRecommendationDto>>> getGuestRecommendations(
            @RequestBody UserPreferenceDto guestPreferences,
            @RequestParam(value = "city", required = false) String city) {

        log.info("Fetching guest recommendations with preferences: {}", guestPreferences);
        List<VehicleRecommendationDto> seq = recommendationService.getRecommendationsBasedOnPreferences(guestPreferences, city);
        return ResponseEntity.ok(ApiResponse.success("Guest recommendations retrieved", seq));
    }
}
