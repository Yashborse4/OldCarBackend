package com.carselling.oldcar.service;

import com.carselling.oldcar.document.VehicleSearchDocument;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.dto.car.CarSearchCriteria;
import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchHitDto;
import com.carselling.oldcar.mapper.VehicleSearchResultMapper;

import com.carselling.oldcar.search.VehicleSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Elasticsearch-backed search and sync service for cars.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class AdvancedSearchService {

    private final VehicleSearchRepository vehicleSearchRepository;
    private final CarRepository carRepository;
    private final VehicleSearchResultMapper vehicleSearchResultMapper;

    /**
     * Search cars using Elasticsearch with keyword + boosting and apply filters
     * in-memory.
     * This avoids direct dependencies on low-level Elasticsearch APIs.
     */
    @Transactional(readOnly = true)
    public Page<CarSearchHitDto> searchCars(CarSearchCriteria criteria,
            Pageable pageable, User currentUser) {
        log.debug("Searching cars with criteria: {}", criteria);

        List<VehicleSearchDocument> searchResults;
        long totalEstimate = 0; // In a real implementations, we'd get total hits from ES response

        String q = (criteria.getQuery() != null) ? criteria.getQuery().trim() : null;
        if (q != null && !q.isBlank()) {
            logSearch(q, currentUser);
        }

        // Logic split based on criteria - simplified for the new Repository API
        boolean verifiedOnly = criteria.getVerifiedDealer() == null
                || Boolean.TRUE.equals(criteria.getVerifiedDealer());

        // Use the generic query method we added to the repository
        searchResults = vehicleSearchRepository.findWithQuery(q, true, verifiedOnly, pageable.getPageNumber(),
                pageable.getPageSize());

        // For pagination of the *generic* list, we might assume the repo returns the
        // 'page' of results.
        // The total count is separate, but for now we can just use the list size or do
        // a count query if needed.
        // For this migration step, strict "Total Elements" might be inaccurate without
        // a separate count call,
        // but getting the data flowing is priority.

        totalEstimate = searchResults.size(); // Placeholder

        // Apply additional memory filters if strictly needed (though ES should handle
        // most)
        // Kept for safety migration match
        List<VehicleSearchDocument> filtered = searchResults.stream()
                .filter(doc -> matchesAny(criteria.getMake(), doc.getBrand()))
                .filter(doc -> matchesAny(criteria.getModel(), doc.getModel()))
                .filter(doc -> matches(criteria.getVariant(), doc.getVariant()))
                .filter(doc -> matchesAny(criteria.getFuelType(), doc.getFuelType()))
                .filter(doc -> matchesAny(criteria.getTransmission(), doc.getTransmission()))
                .filter(doc -> matchesAny(criteria.getLocation(), doc.getCity()))
                .filter(doc -> inRange(criteria.getMinYear(), criteria.getMaxYear(), doc.getYear()))
                .filter(doc -> inRange(
                        criteria.getMinPrice() != null ? BigDecimal.valueOf(criteria.getMinPrice()) : null,
                        criteria.getMaxPrice() != null ? BigDecimal.valueOf(criteria.getMaxPrice()) : null,
                        doc.getPrice() != null ? BigDecimal.valueOf(doc.getPrice()) : null))
                .collect(Collectors.toList());

        List<CarSearchHitDto> hitDtos = filtered.stream()
                .map(vehicleSearchResultMapper::toDto)
                .collect(Collectors.toList());

        return new PageImpl<>(hitDtos, pageable, totalEstimate > 0 ? totalEstimate : hitDtos.size());
    }

    private boolean matches(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return actual.equalsIgnoreCase(expected.trim());
    }

    private boolean matchesAny(List<String> expected, String actual) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        return expected.stream().anyMatch(e -> e != null && e.trim().equalsIgnoreCase(actual.trim()));
    }

    private boolean inRange(Integer min, Integer max, Integer value) {
        if (value == null) {
            return true;
        }
        if (min != null && value < min) {
            return false;
        }
        if (max != null && value > max) {
            return false;
        }
        return true;
    }

    private boolean inRange(BigDecimal min, BigDecimal max, BigDecimal value) {
        if (value == null) {
            return true;
        }
        if (min != null && value.compareTo(min) < 0) {
            return false;
        }
        if (max != null && value.compareTo(max) > 0) {
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    public List<VehicleSearchDocument> suggest(String prefix, int limit) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return vehicleSearchRepository.findSuggestions(prefix).stream()
                .filter(doc -> doc.isActive()) // Enforce Status = ACTIVE
                .filter(doc -> doc.isDealerVerified()) // Enforce verified dealer only
                .limit(Math.max(1, Math.min(limit, 20)))
                .collect(Collectors.toList());
    }

    /**
     * Lightweight index health probe used by REST health endpoint.
     */
    @Transactional(readOnly = true)
    public boolean isIndexHealthy() {
        return vehicleSearchRepository.isClusterHealthy();
    }

    @Transactional(readOnly = true)
    public void syncVehicleToElasticsearch(Long vehicleId) {
        Optional<Car> carOpt = carRepository.findById(vehicleId);
        if (carOpt.isEmpty()) {
            log.warn("syncVehicleToElasticsearch: car {} not found", vehicleId);
            return;
        }
        Car car = carOpt.get();
        VehicleSearchDocument doc = mapCarToDocument(car);
        vehicleSearchRepository.save(doc);
        log.info("Indexed car {} into Elasticsearch", vehicleId);
    }

    public void removeVehicleFromElasticsearch(Long vehicleId) {
        vehicleSearchRepository.deleteById(String.valueOf(vehicleId));
        log.info("Removed car {} from Elasticsearch", vehicleId);
    }

    @Transactional(readOnly = true)
    public int bulkSyncVehiclesToElasticsearch() {
        // 1. Create new index name details
        String aliasName = "vehicle_search";
        String newIndexName = "vehicle_search_"
                + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(java.time.LocalDateTime.now());

        log.info("Starting Blue-Green indexing. New index: {}", newIndexName);

        // 2. Create the new index
        try {
            vehicleSearchRepository.createIndex(newIndexName);
        } catch (Exception e) {
            log.error("Assuming index creation handled or failed: {}", e.getMessage());
        }

        int batchSize = 500;
        int totalProcessed = 0;
        int page = 0;
        Page<Car> carPage;

        do {
            carPage = carRepository.findAll(org.springframework.data.domain.PageRequest.of(page, batchSize));
            List<Car> cars = carPage.getContent();

            if (cars.isEmpty()) {
                break;
            }

            List<VehicleSearchDocument> docs = cars.stream()
                    .map(this::mapCarToDocument)
                    .collect(Collectors.toList());

            // 3. Index into the NEW index
            vehicleSearchRepository.saveAll(newIndexName, docs);
            totalProcessed += docs.size();

            log.info("Indexed batch {}/{} ({} records)", page + 1, carPage.getTotalPages(), docs.size());

            // Pause to reduce load
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Batch indexing interrupted during pause", e);
                break;
            }

            page++;
        } while (carPage.hasNext());

        log.info("Bulk indexed total {} cars into Elasticsearch", totalProcessed);
        // 4. Switch Alias (Simplified success check, assuming no exception if we
        // reached here)
        log.info("Indexing complete. Switching alias {} to {}", aliasName, newIndexName);
        try {
            vehicleSearchRepository.updateAlias(aliasName, newIndexName);
            log.info("Blue-Green deployment successful.");
        } catch (Exception e) {
            log.error("Failed to switch alias", e);
            // Cleanup
            vehicleSearchRepository.deleteIndex(newIndexName);
            throw new RuntimeException("Blue-Green alias switch failed", e);
        }

        log.info("Bulk indexed total {} cars into Elasticsearch", totalProcessed);
        return totalProcessed;
    }

    @Transactional(readOnly = true)
    public int incrementalSyncVehicles(java.time.LocalDateTime since) {
        log.info("Starting incremental indexing for cars updated after {}", since);
        int batchSize = 500;
        int totalProcessed = 0;
        int page = 0;
        Page<Car> carPage;

        do {
            carPage = carRepository.findByUpdatedAtAfter(since,
                    org.springframework.data.domain.PageRequest.of(page, batchSize));
            List<Car> cars = carPage.getContent();

            if (cars.isEmpty()) {
                break;
            }

            List<VehicleSearchDocument> docs = cars.stream()
                    .map(this::mapCarToDocument)
                    .collect(Collectors.toList());

            // For incremental, we write to the active alias "vehicle_search"
            // We reuse the method we created but pass the alias name
            vehicleSearchRepository.saveAll("vehicle_search", docs);
            totalProcessed += docs.size();

            log.info("Incrementally indexed batch {}/{} ({} records)", page + 1, carPage.getTotalPages(), docs.size());

            // Pause to reduce load
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Batch indexing interrupted", e);
                break;
            }

            page++;
        } while (carPage.hasNext());

        log.info("Incremental indexing completed. Total processed: {}", totalProcessed);
        return totalProcessed;
    }

    private VehicleSearchDocument mapCarToDocument(Car car) {
        User owner = car.getOwner();
        boolean verifiedDealer = owner != null && Boolean.TRUE.equals(owner.isDealerVerified());

        VehicleSearchDocument doc = VehicleSearchDocument.builder()
                .id(String.valueOf(car.getId()))
                .carId(car.getId())
                .dealerId(owner != null ? owner.getId() : null)
                .brand(car.getMake())
                .model(car.getModel())
                .variant(null) // Variant not directly available in Car model usually, or needs extraction
                .year(car.getYear())
                .price(car.getPrice() != null ? car.getPrice().doubleValue() : null)
                .mileage(car.getMileage())
                .fuelType(car.getFuelType())
                .transmission(car.getTransmission())
                .city(owner != null ? owner.getLocation() : null)
                .thumbnailImageUrl(resolveThumbnail(car))
                .viewCount(car.getViewCount() != null ? car.getViewCount().intValue() : 0)
                .leadCount(0)
                .active(Boolean.TRUE.equals(car.getIsActive()) && !Boolean.TRUE.equals(car.getIsSold()))
                .dealerVerified(verifiedDealer)
                .carApproved(true) // Default to true or add logic if 'isApproved' exists
                .createdAt(car.getCreatedAt() != null ? car.getCreatedAt().toInstant(ZoneOffset.UTC) : null)
                .normalizedBrand(car.getMake() != null ? car.getMake().toLowerCase() : null)
                .normalizedModel(car.getModel() != null ? car.getModel().toLowerCase() : null)
                .normalizedCity(owner != null && owner.getLocation() != null ? owner.getLocation().toLowerCase() : null)
                .build();

        // Removed methods: buildSearchableText, calculateSearchBoost, setSuggest as
        // they are no longer in VehicleSearchDocument
        return doc;
    }

    @Transactional(readOnly = true)
    public Page<VehicleSearchDocument> findSimilarVehicles(Long carId, Pageable pageable) {
        Optional<VehicleSearchDocument> docOpt = vehicleSearchRepository.findById(String.valueOf(carId));
        if (docOpt.isEmpty()) {
            // Try to load from DB if not in index
            Optional<Car> carOpt = carRepository.findById(carId);
            if (carOpt.isPresent()) {
                syncVehicleToElasticsearch(carId);
                docOpt = vehicleSearchRepository.findById(String.valueOf(carId));
            }
            if (docOpt.isEmpty()) {
                return Page.empty(pageable);
            }
        }

        VehicleSearchDocument doc = docOpt.get();
        BigDecimal price = doc.getPrice() != null ? BigDecimal.valueOf(doc.getPrice()) : BigDecimal.ZERO;
        BigDecimal priceMin = price.multiply(BigDecimal.valueOf(0.8)); // -20%
        BigDecimal priceMax = price.multiply(BigDecimal.valueOf(1.2)); // +20%

        return vehicleSearchRepository.findSimilarVehicles(
                doc.getBrand(),
                doc.getModel(),
                priceMin,
                priceMax,
                pageable);
    }

    private final com.carselling.oldcar.repository.SearchHistoryRepository searchHistoryRepository;

    @org.springframework.scheduling.annotation.Async
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void logSearch(String query, User user) {
        if (query == null || query.isBlank() || query.length() < 2) {
            return;
        }
        try {
            com.carselling.oldcar.model.SearchHistory history = com.carselling.oldcar.model.SearchHistory.builder()
                    .query(query.trim())
                    .user(user)
                    .build();
            searchHistoryRepository.save(history);
        } catch (Exception e) {
            log.warn("Failed to log search history: {}", e.getMessage());
        }
    }

    public List<String> getTrendingSearchTerms(int limit) {
        return searchHistoryRepository.findTrendingSearches(org.springframework.data.domain.PageRequest.of(0, limit));
    }

    public List<String> getRecentSearches(User user, int limit) {
        if (user == null) {
            return List.of();
        }
        return searchHistoryRepository.findRecentSearchesByUser(user.getId(),
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    private String resolveThumbnail(Car car) {
        if (car.getImages() != null && !car.getImages().isEmpty()) {
            return car.getImages().get(0);
        }
        return car.getImageUrl();
    }
}
