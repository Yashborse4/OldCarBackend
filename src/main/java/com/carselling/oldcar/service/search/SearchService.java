package com.carselling.oldcar.service.search;

import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchHitDto;
import com.carselling.oldcar.dto.car.CarSearchDtos;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Strategy interface for Car Search operations.
 * Implementations:
 * - ElasticSearchServiceImpl (Primary, enabled via property)
 * - DatabaseSearchServiceImpl (Fallback, enabled when ES is disabled)
 */
public interface SearchService {

    /**
     * Search for cars using the provided request object which contains criteria and
     * pagination.
     */
    Page<CarSearchHitDto> searchCars(CarSearchDtos.CarSearchRequest request);

    /**
     * Get autocomplete suggestions.
     * Default implementation returns empty list if not supported.
     */
    default List<String> suggest(String prefix, int limit) {
        return List.of();
    }

    /**
     * Check health of the search service.
     */
    boolean isHealthy();
}
