package com.carselling.oldcar.controller;

import com.carselling.oldcar.document.VehicleSearchDocument;
import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchHitDto;
import com.carselling.oldcar.mapper.VehicleSearchResultMapper;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.service.AdvancedSearchService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class CarSearchGraphQlController {

    private final AdvancedSearchService advancedSearchService;
    private final VehicleSearchResultMapper vehicleSearchResultMapper;
    private final UserRepository userRepository;

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
        private String city;
        private Integer minYear;
        private Integer maxYear;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private Boolean verifiedDealer;
        private Integer page;
        private Integer size;
    }

    @QueryMapping(name = "searchCars")
    public CarSearchPagePayload searchCars(@Argument("input") CarSearchInput input, Principal principal) {
        int page = input.getPage() != null ? Math.max(0, input.getPage()) : 0;
        int size = input.getSize() != null ? Math.max(1, Math.min(input.getSize(), 50)) : 20;
        Pageable pageable = PageRequest.of(page, size);

        AdvancedSearchService.CarSearchCriteria criteria = AdvancedSearchService.CarSearchCriteria.builder()
                .keyword(trim(input.getKeyword()))
                .brands(trimList(input.getBrands()))
                .models(trimList(input.getModels()))
                .variant(trim(input.getVariant()))
                .fuelTypes(trimList(input.getFuelTypes()))
                .transmissions(trimList(input.getTransmissions()))
                .city(trim(input.getCity()))
                .minYear(input.getMinYear())
                .maxYear(input.getMaxYear())
                .minPrice(input.getMinPrice())
                .maxPrice(input.getMaxPrice())
                .verifiedDealer(input.getVerifiedDealer())
                .build();

        // Fetch current user if authenticated
        User currentUser = null;
        if (principal != null) {
            currentUser = userRepository.findByEmail(principal.getName()).orElse(null);
        }

        Page<VehicleSearchDocument> result = advancedSearchService.searchCars(criteria, pageable);

        final User finalCurrentUser = currentUser;
        List<CarSearchHitDto> hits = result.getContent().stream()
                .map(doc -> vehicleSearchResultMapper.applyRoleBasedMasking(doc, finalCurrentUser))
                .map(CarSearchHitDto::fromDocument)
                .collect(Collectors.toList());

        Page<CarSearchHitDto> dtoPage = new PageImpl<>(hits, pageable, result.getTotalElements());

        return new CarSearchPagePayload(
                dtoPage.getContent(),
                dtoPage.getTotalPages(),
                dtoPage.getTotalElements(),
                dtoPage.getNumber(),
                dtoPage.getSize());
    }

    @QueryMapping(name = "searchCarSuggestions")
    public List<String> searchCarSuggestions(@Argument("prefix") String prefix,
            @Argument("limit") Integer limit) {
        int safeLimit = limit != null ? Math.max(1, Math.min(limit, 20)) : 10;
        return advancedSearchService.suggest(prefix, safeLimit).stream()
                .map(doc -> String.join(" ",
                        nonNull(doc.getBrand()),
                        nonNull(doc.getModel())))
                .distinct()
                .limit(safeLimit)
                .collect(Collectors.toList());
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private List<String> trimList(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(this::trim)
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String nonNull(String value) {
        return value == null ? "" : value.trim();
    }
}
