package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchHitDto;

import com.carselling.oldcar.service.car.CarSearchService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Controller
public class CarSearchGraphQlController {

    private final CarSearchService carSearchService;

    @Data
    @AllArgsConstructor
    public static class CarSearchPagePayload {
        private List<CarSearchHitDto> content;
        private int totalPages;
        private long totalElements;
        private int pageNumber;
        private int pageSize;
    }

    @Data
    public static class CarSearchInput {
        private String keyword;
        private List<String> brands;
        private List<String> models;
        private String variant;
        private List<String> fuelTypes;
        private List<String> transmissions;
        private List<String> cities;
        private Integer minYear;
        private Integer maxYear;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private Boolean verifiedDealer;
        private Integer page;
        private Integer size;
    }

    @QueryMapping(name = "searchCars")
    @PreAuthorize("permitAll()")
    public CarSearchPagePayload searchCars(@Argument("input") CarSearchInput input, Principal principal) {

        // Map GraphQL Input to Unified Request DTO
        com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchRequest request = com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchRequest
                .builder()
                .keyword(input.getKeyword())
                .brands(input.getBrands())
                .models(input.getModels())
                .variant(input.getVariant())
                .fuelTypes(input.getFuelTypes())
                .transmissions(input.getTransmissions())
                .cities(input.getCities())
                .minYear(input.getMinYear())
                .maxYear(input.getMaxYear())
                .minPrice(input.getMinPrice())
                .maxPrice(input.getMaxPrice())
                .verifiedDealer(input.getVerifiedDealer())
                .page(input.getPage())
                .size(input.getSize())
                .build();
        
        // Input Validation (Security)
        if (input.getBrands() != null && input.getBrands().size() > 20) {
            throw new IllegalArgumentException("Too many brands specified (max 20)");
        }
        if (input.getModels() != null && input.getModels().size() > 20) {
            throw new IllegalArgumentException("Too many models specified (max 20)");
        }
        if (input.getCities() != null && input.getCities().size() > 20) {
             throw new IllegalArgumentException("Too many cities specified (max 20)");
        }
        if (input.getKeyword() != null && input.getKeyword().length() > 100) {
             throw new IllegalArgumentException("Keyword too long (max 100 chars)");
        }

        Page<CarSearchHitDto> dtoPage = carSearchService.search(request);

        return new CarSearchPagePayload(
                dtoPage.getContent(),
                dtoPage.getTotalPages(),
                dtoPage.getTotalElements(),
                dtoPage.getNumber(),
                dtoPage.getSize());
    }

    @QueryMapping(name = "searchCarSuggestions")
    @PreAuthorize("permitAll()")
    public List<String> searchCarSuggestions(@Argument("prefix") String prefix,
            @Argument("limit") Integer limit) {
        int safeLimit = limit != null ? Math.max(1, Math.min(limit, 20)) : 10;
        return carSearchService.suggest(prefix, safeLimit);
    }
}
