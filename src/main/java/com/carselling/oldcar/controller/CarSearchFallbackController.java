package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.dto.car.CarSearchCriteria;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.CarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Fallback REST API for database-backed car search.
 * This controller is loaded only when Elasticsearch is disabled.
 */
@RestController
@RequestMapping("/api/search/cars")
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class CarSearchFallbackController {

    private final CarService carService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CarResponse>>> searchCars(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "brands", required = false) String brands,
            @RequestParam(value = "models", required = false) String models,
            @RequestParam(value = "fuelTypes", required = false) String fuelTypes,
            @RequestParam(value = "transmissions", required = false) String transmissions,
            @RequestParam(value = "cities", required = false) String cities,
            @RequestParam(value = "minYear", required = false) Integer minYear,
            @RequestParam(value = "maxYear", required = false) Integer maxYear,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        log.debug("Database-backed car search called with keyword: {}", keyword);

        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(0, page);

        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Build search criteria from request parameters
        CarSearchCriteria criteria = CarSearchCriteria.builder()
                .query(keyword)
                .make(brands)
                .model(models)
                .fuelType(fuelTypes)
                .transmission(transmissions)
                .location(cities)
                .minYear(minYear)
                .maxYear(maxYear)
                .minPrice(minPrice != null ? minPrice.longValue() : null)
                .maxPrice(maxPrice != null ? maxPrice.longValue() : null)
                .build();

        Page<CarResponse> result = carService.searchVehicles(criteria, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Search completed successfully",
                "Database-backed car search results",
                result));
    }
}
