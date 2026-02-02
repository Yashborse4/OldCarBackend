package com.carselling.oldcar.search;

import com.carselling.oldcar.dto.car.CarSearchCriteria;
import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchHitDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Strategy interface for unified Car Search operations.
 * Implementations provide search capabilities via different backends (ES, DB).
 */
public interface CarSearchProvider {

    /**
     * Search cars based on criteria.
     * 
     * @param criteria The standardized search criteria.
     * @param pageable Pagination info.
     * @return Page of search hits.
     */
    Page<CarSearchHitDto> search(CarSearchCriteria criteria, Pageable pageable);

    /**
     * Get autocomplete suggestions.
     * Default implementation returns empty list if not supported.
     */
    default List<String> suggest(String prefix, int limit) {
        return java.util.Collections.emptyList();
    }

    default List<String> getTrendingSearchTerms(int limit) {
        return java.util.Collections.emptyList();
    }

    default List<String> getRecentSearches(com.carselling.oldcar.model.User user, int limit) {
        return java.util.Collections.emptyList();
    }
}
