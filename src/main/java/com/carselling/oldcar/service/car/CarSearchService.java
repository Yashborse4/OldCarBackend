package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.car.CarSearchCriteria;
import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchHitDto;
import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchRequest;
import com.carselling.oldcar.exception.BusinessException;
import com.carselling.oldcar.search.CarSearchProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.carselling.oldcar.model.User;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service facade for unified car search operations.
 * Centralizes input sanitization and criteria construction for both REST and
 * GraphQL APIs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CarSearchService {

    private final CarSearchProvider searchProvider;

    /**
     * Search cars based on the unified request DTO.
     * Handles trimming, defaults, and validation.
     */
    public Page<CarSearchHitDto> search(CarSearchRequest request) {
        log.debug("Processing search request: {}", request);

        // Map Request to Criteria with sanitization
        CarSearchCriteria criteria = CarSearchCriteria.builder()
                .query(trim(request.getKeyword()))
                .make(trimList(request.getBrands()))
                .model(trimList(request.getModels()))
                .variant(trim(request.getVariant()))
                .fuelType(trimList(request.getFuelTypes()))
                .transmission(trimList(request.getTransmissions()))
                .location(trimList(request.getCities()))
                .minYear(request.getMinYear())
                .maxYear(request.getMaxYear())
                .minPrice(request.getMinPrice() != null ? request.getMinPrice().longValue() : null)
                .maxPrice(request.getMaxPrice() != null ? request.getMaxPrice().longValue() : null)
                .verifiedDealer(request.getVerifiedDealer())
                .sortBy(request.getSort() != null ? request.getSort() : "relevance")
                .sortDirection("desc") // Default to desc, or could be added to request DTO
                .build();

        int page = request.getPage() != null ? Math.max(0, request.getPage()) : 0;
        int size = request.getSize() != null ? Math.max(1, Math.min(request.getSize(), 50)) : 20;

        int window = page * size;
        if (window > 1000) {
            throw new BusinessException("Requested result window is too large. Please narrow your filters.");
        }

        Pageable pageable = PageRequest.of(page, size);

        // Perform the search
        Page<CarSearchHitDto> result = searchProvider.search(criteria, pageable);
        log.debug("Search completed with {} results on page {}", result.getTotalElements(), result.getNumber());
        return result;
    }

    /**
     * Suggest search terms or vehicles.
     */
    public List<String> suggest(String prefix, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return searchProvider.suggest(prefix, safeLimit);
    }

    public List<String> getTrendingSearchTerms(int limit) {
        return searchProvider.getTrendingSearchTerms(limit);
    }

    public List<String> getRecentSearches(User user, int limit) {
        return searchProvider.getRecentSearches(user, limit);
    }

    // --- Helper Methods ---

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

}
