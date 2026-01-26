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

        Page<VehicleSearchDocument> basePage;
        if (criteria.getQuery() != null && !criteria.getQuery().isBlank()) {
            String q = criteria.getQuery().trim();

            // Use security-conscious method that enforces public visibility rules
            if (criteria.getMake() != null || criteria.getModel() != null) {
                basePage = vehicleSearchRepository.findWithBoostingSearchAndFilters(q, criteria.getMake(),
                        criteria.getModel(), pageable);
            } else {
                basePage = vehicleSearchRepository.findWithBoostingSearchAndActiveTrueAndDealerVerifiedTrue(q,
                        pageable);
            }
        } else {
            // Push basic visibility (active + verified dealer) into Elasticsearch when
            // possible
            if (criteria.getVerifiedDealer() == null || Boolean.TRUE.equals(criteria.getVerifiedDealer())) {
                basePage = vehicleSearchRepository.findByActiveTrueAndDealerVerifiedTrue(pageable);
            } else {
                // This case should rarely happen for public searches, but handle it safely
                basePage = vehicleSearchRepository.findByActiveTrueAndDealerVerifiedTrue(pageable);
            }
        }

        // Apply additional filters in-memory for fields not handled by ES queries
        List<VehicleSearchDocument> filtered = basePage.getContent().stream()
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
                // Security: These should already be filtered by ES, but double-check
                .filter(doc -> doc.isActive()) // Enforce Status = ACTIVE
                .filter(doc -> doc.isDealerVerified()) // Enforce verified dealer only
                .filter(doc -> criteria.getVerifiedDealer() == null ||
                        Boolean.TRUE.equals(criteria.getVerifiedDealer()) == doc.isDealerVerified())
                .collect(Collectors.toList());

        int totalElements = filtered.size();
        int fromIndex = Math.min((int) pageable.getOffset(), totalElements);
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), totalElements);

        List<VehicleSearchDocument> pageContent = filtered.subList(fromIndex, toIndex);

        // Map to DTO
        List<CarSearchHitDto> hitDtos = pageContent.stream()
                .map(vehicleSearchResultMapper::toDto)
                .collect(Collectors.toList());

        return new PageImpl<>(hitDtos, pageable, totalElements);
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
        try {
            // A small count query is usually enough to validate connectivity.
            vehicleSearchRepository.count();
            return true;
        } catch (Exception ex) {
            log.warn("Elasticsearch health check failed: {}", ex.getMessage());
            return false;
        }
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
    public void bulkSyncVehiclesToElasticsearch() {
        List<Car> cars = carRepository.findAll();
        List<VehicleSearchDocument> docs = cars.stream()
                .map(this::mapCarToDocument)
                .collect(Collectors.toList());
        vehicleSearchRepository.saveAll(docs);
        log.info("Bulk indexed {} cars into Elasticsearch", docs.size());
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

    public List<String> getTrendingSearchTerms(int limit) {
        // This would typically come from search analytics
        // For now, return popular vehicle makes/models
        return List.of(
                "Toyota Camry", "Honda Civic", "BMW 3 Series", "Mercedes C-Class",
                "Audi A4", "Ford F-150", "Chevrolet Silverado", "Tesla Model 3",
                "Hyundai Elantra", "Nissan Altima").stream().limit(Math.max(1, limit)).collect(Collectors.toList());
    }

    private String resolveThumbnail(Car car) {
        if (car.getImages() != null && !car.getImages().isEmpty()) {
            return car.getImages().get(0);
        }
        return car.getImageUrl();
    }
}
