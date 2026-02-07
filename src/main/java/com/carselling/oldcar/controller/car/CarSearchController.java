package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchHitDto;
import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchRequest;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.car.CarSearchService;
import com.carselling.oldcar.annotation.RateLimit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for Elasticsearch-backed car search.
 */

@RestController
@RequestMapping({ "/api/search/cars", "/api/cars/search" })
@RequiredArgsConstructor
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "Car Search", description = "Vehicle search and discovery endpoints")
public class CarSearchController {

        private final CarSearchService carSearchService;
        private final com.carselling.oldcar.service.MobileAppService mobileAppService;

        @GetMapping
        @RateLimit(capacity = 120, refill = 60, refillPeriod = 1)
        @io.swagger.v3.oas.annotations.Operation(summary = "Search cars", description = "Search for vehicles with advanced filtering")
        public ResponseEntity<ApiResponse<Page<CarSearchHitDto>>> searchCars(
                        @jakarta.validation.Valid @org.springframework.web.bind.annotation.ModelAttribute CarSearchRequest request) {

                Page<CarSearchHitDto> result = carSearchService.search(request);

                return ResponseEntity.ok(ApiResponse.success(
                                "Search completed successfully",
                                "Car search results",
                                result));
        }

        @GetMapping("/suggestions")
        @RateLimit(capacity = 60, refill = 30, refillPeriod = 1)
        @io.swagger.v3.oas.annotations.Operation(summary = "Get suggestions", description = "Get search suggestions/autocomplete")
        public ResponseEntity<ApiResponse<List<String>>> suggest(
                        @RequestParam("q") String prefix,
                        @RequestParam(value = "limit", defaultValue = "10") int limit) {

                int safeLimit = Math.max(1, Math.min(limit, 20));
                List<String> suggestions = carSearchService.suggest(prefix, safeLimit);

                return ResponseEntity.ok(ApiResponse.success(
                                "Suggestions generated",
                                "Autocomplete suggestions",
                                suggestions));
        }

        /**
         * Mobile-optimized search suggestions
         * Categorizes results into General, Makes, and Popular
         */
        @GetMapping("/suggestions/mobile")
        @RateLimit(capacity = 60, refill = 30, refillPeriod = 1)
        @io.swagger.v3.oas.annotations.Operation(summary = "Mobile suggestions", description = "Get categorized suggestions for mobile")
        public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> suggestMobile(
                        @RequestParam("q") String query,
                        @RequestParam(value = "limit", defaultValue = "10") int limit) {

                int safeLimit = Math.max(1, Math.min(limit, 20));
                java.util.Map<String, Object> suggestions = mobileAppService.getSearchSuggestions(query, safeLimit);

                return ResponseEntity.ok(ApiResponse.success(
                                "Mobile suggestions generated",
                                "Categorized suggestions",
                                suggestions));
        }

        @GetMapping("/history")
        @RateLimit(capacity = 60, refill = 30, refillPeriod = 1)
        @io.swagger.v3.oas.annotations.Operation(summary = "Search history", description = "Get recent search history for user")
        public ResponseEntity<ApiResponse<List<String>>> getRecentSearches(
                        @AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser,
                        @RequestParam(value = "limit", defaultValue = "10") int limit) {

                if (currentUser == null) {
                        return ResponseEntity.ok(ApiResponse.success("No user", "Recent searches", List.of()));
                }

                // Extract user ID from UserPrincipal
                Long userId = currentUser.getId();

                // Call service with user ID
                List<String> recentSearches = carSearchService.getRecentSearches(userId, limit);

                return ResponseEntity.ok(ApiResponse.success(
                                "Recent searches",
                                "Recent searches",
                                recentSearches));
        }

        @GetMapping("/trending")
        @io.swagger.v3.oas.annotations.Operation(summary = "Trending searches", description = "Get trending search terms")
        public ResponseEntity<ApiResponse<List<String>>> getTrendingSearches(
                        @RequestParam(value = "limit", defaultValue = "10") int limit) {
                List<String> trending = carSearchService.getTrendingSearchTerms(limit);
                return ResponseEntity.ok(ApiResponse.success(
                                "Trending searches",
                                "Trending searches",
                                trending));
        }

        @GetMapping("/health")
        public ResponseEntity<ApiResponse<Object>> health() {
                // Simplified health check
                return ResponseEntity.ok(ApiResponse.success(
                                "Search service is reachable",
                                "Search backend is active"));
        }
}
