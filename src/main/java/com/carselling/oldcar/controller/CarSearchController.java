package com.carselling.oldcar.controller;

import com.carselling.oldcar.document.VehicleSearchDocument;
import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchHitDto;
import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchRequest;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.AdvancedSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API for Elasticsearch-backed car search.
 */
@RestController
@RequestMapping("/api/search/cars")
@RequiredArgsConstructor
@Slf4j
public class CarSearchController {

    private final AdvancedSearchService advancedSearchService;

        @GetMapping
        public ResponseEntity<ApiResponse<Page<CarSearchHitDto>>> searchCars(
                        @RequestParam(value = "q", required = false) String keyword,
                        @RequestParam(value = "brand", required = false) String brand,
                        @RequestParam(value = "model", required = false) String model,
                        @RequestParam(value = "variant", required = false) String variant,
                        @RequestParam(value = "fuelType", required = false) String fuelType,
                        @RequestParam(value = "transmission", required = false) String transmission,
                        @RequestParam(value = "city", required = false) String city,
                        @RequestParam(value = "minYear", required = false) Integer minYear,
                        @RequestParam(value = "maxYear", required = false) Integer maxYear,
                        @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
                        @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
                        @RequestParam(value = "verifiedDealer", required = false) Boolean verifiedDealer,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size) {

                int safeSize = Math.max(1, Math.min(size, 50));
                int safePage = Math.max(0, page);

                Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

                CarSearchRequest criteria = CarSearchRequest.builder()
                                .keyword(trim(keyword))
                                .brand(trim(brand))
                                .model(trim(model))
                                .variant(trim(variant))
                                .fuelType(trim(fuelType))
                                .transmission(trim(transmission))
                                .city(trim(city))
                                .minYear(minYear)
                                .maxYear(maxYear)
                                .minPrice(minPrice)
                                .maxPrice(maxPrice)
                                .verifiedDealer(verifiedDealer)
                                .build();

                Page<VehicleSearchDocument> result = advancedSearchService.searchCars(
                                AdvancedSearchService.CarSearchCriteria.builder()
                                                .keyword(criteria.getKeyword())
                                                .brand(criteria.getBrand())
                                                .model(criteria.getModel())
                                                .variant(criteria.getVariant())
                                                .fuelType(criteria.getFuelType())
                                                .transmission(criteria.getTransmission())
                                                .city(criteria.getCity())
                                                .minYear(criteria.getMinYear())
                                                .maxYear(criteria.getMaxYear())
                                                .minPrice(criteria.getMinPrice())
                                                .maxPrice(criteria.getMaxPrice())
                                                .verifiedDealer(criteria.getVerifiedDealer())
                                                .build(),
                                pageable);

                List<CarSearchHitDto> hits = result.getContent().stream()
                                .map(CarSearchHitDto::fromDocument)
                                .collect(Collectors.toList());

                Page<CarSearchHitDto> dtoPage = new PageImpl<>(hits, pageable, result.getTotalElements());

                return ResponseEntity.ok(ApiResponse.success(
                                "Search completed successfully",
                                "Elasticsearch car search results",
                                dtoPage));
        }

        @GetMapping("/suggestions")
        public ResponseEntity<ApiResponse<List<String>>> suggest(
                        @RequestParam("q") String prefix,
                        @RequestParam(value = "limit", defaultValue = "10") int limit) {

                int safeLimit = Math.max(1, Math.min(limit, 20));
                List<String> suggestions = advancedSearchService.suggest(prefix, safeLimit).stream()
                                .map(doc -> String.join(" ",
                                                nonNull(doc.getBrand()),
                                                nonNull(doc.getModel())))
                                .distinct()
                                .limit(safeLimit)
                                .collect(Collectors.toList());

                return ResponseEntity.ok(ApiResponse.success(
                                "Suggestions generated",
                                "Autocomplete suggestions for car search",
                                suggestions));
        }

        @GetMapping("/health")
        public ResponseEntity<ApiResponse<Object>> health() {
                boolean healthy = advancedSearchService.isIndexHealthy();
                if (healthy) {
                        return ResponseEntity.ok(ApiResponse.success(
                                        "Search index is healthy",
                                        "Elasticsearch car_search_index is reachable"));
                }
                return ResponseEntity.ok(ApiResponse.error(
                                "Search index is not healthy",
                                "Elasticsearch car_search_index is not reachable or misconfigured"));
        }

        private String trim(String value) {
                return value == null ? null : value.trim();
        }

        private String nonNull(String value) {
                return value == null ? "" : value.trim();
        }
}
