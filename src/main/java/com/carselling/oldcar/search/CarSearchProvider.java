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
     * Suggest search terms or vehicles.
     * 
     * @param prefix Query prefix.
     * @param limit  Max suggestions.
     * @return List of suggestion strings.
     */
    default List<String> suggest(String prefix, int limit) {
        return List.of();
    }
}
