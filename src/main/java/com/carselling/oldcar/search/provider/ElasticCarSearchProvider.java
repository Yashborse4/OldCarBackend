package com.carselling.oldcar.search.provider;

import com.carselling.oldcar.dto.car.CarSearchCriteria;
import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchHitDto;
import com.carselling.oldcar.search.CarSearchProvider;
import com.carselling.oldcar.service.AdvancedSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Elasticsearch implementation of CarSearchProvider.
 * Delegates to AdvancedSearchService for core ES logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class ElasticCarSearchProvider implements CarSearchProvider {

    private final AdvancedSearchService advancedSearchService;

    @Override
    public Page<CarSearchHitDto> search(CarSearchCriteria criteria, Pageable pageable) {
        log.debug("Executing Elasticsearch search for criteria: {}", criteria);
        // User context is not strictly needed for public search filtering in current
        // AdvancedSearchService implementation
        return advancedSearchService.searchCars(criteria, pageable, null);
    }

    @Override
    public List<String> suggest(String prefix, int limit) {
        return advancedSearchService.suggest(prefix, limit).stream()
                .map(doc -> String.join(" ",
                        doc.getBrand() != null ? doc.getBrand() : "",
                        doc.getModel() != null ? doc.getModel() : "").trim())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }
}
