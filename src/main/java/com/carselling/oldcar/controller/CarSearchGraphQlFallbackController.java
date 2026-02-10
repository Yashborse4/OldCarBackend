package com.carselling.oldcar.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.Collections;
import java.util.List;

/**
 * Fallback GraphQL controller for search queries when Elasticsearch is
 * disabled.
 * Provides stub implementations that return empty results, preventing
 * "Unmapped fields" warnings in the GraphQL schema inspection.
 */
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
@Controller
@Slf4j
public class CarSearchGraphQlFallbackController {

    @Data
    @AllArgsConstructor
    public static class CarSearchPagePayload {
        private List<?> content;
        private int totalPages;
        private long totalElements;
        private int pageNumber;
        private int pageSize;
    }

    @QueryMapping(name = "searchCars")
    public CarSearchPagePayload searchCars(@Argument("input") Object input) {
        log.debug("Search is unavailable: Elasticsearch is disabled");
        return new CarSearchPagePayload(Collections.emptyList(), 0, 0, 0, 0);
    }

    @QueryMapping(name = "searchCarSuggestions")
    public List<String> searchCarSuggestions(@Argument("prefix") String prefix,
            @Argument("limit") Integer limit) {
        log.debug("Search suggestions unavailable: Elasticsearch is disabled");
        return Collections.emptyList();
    }
}
