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
import java.util.stream.Collectors;

/**
 * OpenSearch implementation of CarSearchProvider.
 * Delegates to AdvancedSearchService for core OpenSearch logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
// Only active if opensearch is enabled (or reuse elasticsearch.enabled prop key
// for compatibility if lazily migrating props)
// Current docker setup implies we want this to be the main one.
// Let's assume we stick to 'elasticsearch.enabled' property for now to avoid
// changing application.yml keys extensively,
// or check for 'opensearch.enabled'
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
public class OpenSearchCarSearchProvider implements CarSearchProvider {

    private final AdvancedSearchService advancedSearchService;

    @Override
    public Page<CarSearchHitDto> search(CarSearchCriteria criteria, Pageable pageable) {
        log.debug("Executing OpenSearch search for criteria: {}", criteria);
        // User context is null here as provider interface doesn't always pass it,
        // relying on Public/Anonymous rules in Service
        return advancedSearchService.searchCars(criteria, pageable, null);
    }

    @Override
    public List<String> suggest(String prefix, int limit) {
        return advancedSearchService.suggest(prefix, limit).stream()
                .map(doc -> {
                    String brand = doc.getBrand() != null ? doc.getBrand() : "";
                    String model = doc.getModel() != null ? doc.getModel() : "";
                    return (brand + " " + model).trim();
                })
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getTrendingSearchTerms(int limit) {
        return advancedSearchService.getTrendingSearchTerms(limit);
    }

    @Override
    public List<String> getRecentSearches(com.carselling.oldcar.model.User user, int limit) {
        return advancedSearchService.getRecentSearches(user, limit);
    }
}
