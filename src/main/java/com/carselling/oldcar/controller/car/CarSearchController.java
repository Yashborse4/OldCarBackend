package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchHitDto;
import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchRequest;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.car.CarSearchService;
import com.carselling.oldcar.annotation.RateLimit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class CarSearchController {

        private final CarSearchService carSearchService;
        private final com.carselling.oldcar.service.MobileAppService mobileAppService;

        @GetMapping
        @RateLimit(capacity = 120, refill = 60, refillPeriod = 1)
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

        @GetMapping("/health")
        public ResponseEntity<ApiResponse<Object>> health() {
                // Simplified health check
                return ResponseEntity.ok(ApiResponse.success(
                                "Search service is reachable",
                                "Search backend is active"));
        }
}
