package com.carselling.oldcar.repository.search;

import com.carselling.oldcar.document.VehicleSearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Repository for Elasticsearch vehicle search operations
 */
@Repository
public interface VehicleSearchRepository extends ElasticsearchRepository<VehicleSearchDocument, String> {

    /**
     * Full-text search across all vehicle fields
     */
    @Query("{\"multi_match\": {\"query\": \"?0\", \"fields\": [\"make^3\", \"model^3\", \"searchableText^2\", \"description\", \"features\", \"tags\"]}}")
    Page<VehicleSearchDocument> findByFullTextSearch(String query, Pageable pageable);

    /**
     * Search by make and model
     */
    Page<VehicleSearchDocument> findByMakeAndModel(String make, String model, Pageable pageable);

    /**
     * Search by make
     */
    Page<VehicleSearchDocument> findByMake(String make, Pageable pageable);

    /**
     * Price range search
     */
    Page<VehicleSearchDocument> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    /**
     * Year range search
     */
    Page<VehicleSearchDocument> findByYearBetween(Integer minYear, Integer maxYear, Pageable pageable);

    /**
     * Search by fuel type
     */
    Page<VehicleSearchDocument> findByFuelType(String fuelType, Pageable pageable);

    /**
     * Search by transmission
     */
    Page<VehicleSearchDocument> findByTransmission(String transmission, Pageable pageable);

    /**
     * Search by body type
     */
    Page<VehicleSearchDocument> findByBodyType(String bodyType, Pageable pageable);

    /**
     * Search by location
     */
    Page<VehicleSearchDocument> findByLocationContaining(String location, Pageable pageable);

    /**
     * Search by city
     */
    Page<VehicleSearchDocument> findByCity(String city, Pageable pageable);

    /**
     * Find featured vehicles
     */
    Page<VehicleSearchDocument> findByIsFeaturedTrue(Pageable pageable);

    /**
     * Find verified vehicles
     */
    Page<VehicleSearchDocument> findByIsVerifiedTrue(Pageable pageable);

    /**
     * Search by owner type
     */
    Page<VehicleSearchDocument> findByOwnerType(String ownerType, Pageable pageable);

    /**
     * Advanced geo-spatial search within radius
     */
    @Query("{\"bool\": {\"filter\": {\"geo_distance\": {\"distance\": \"?2km\", \"geoLocation\": {\"lat\": ?0, \"lon\": ?1}}}}}")
    Page<VehicleSearchDocument> findByLocationWithinRadius(Double latitude, Double longitude, Double radiusKm, Pageable pageable);

    /**
     * Complex search with multiple filters
     */
    @Query("{"
        + "\"bool\": {"
        + "  \"must\": ["
        + "    {\"multi_match\": {\"query\": \"?0\", \"fields\": [\"make^3\", \"model^3\", \"searchableText\"]}}"
        + "  ],"
        + "  \"filter\": ["
        + "    {\"range\": {\"price\": {\"gte\": ?1, \"lte\": ?2}}},"
        + "    {\"range\": {\"year\": {\"gte\": ?3, \"lte\": ?4}}}"
        + "  ]"
        + "}}")
    Page<VehicleSearchDocument> findWithComplexFilters(
        String query, 
        BigDecimal minPrice, 
        BigDecimal maxPrice,
        Integer minYear,
        Integer maxYear,
        Pageable pageable
    );

    /**
     * Search suggestions for auto-complete
     */
    @Query("{\"suggest\": {\"vehicle_suggest\": {\"prefix\": \"?0\", \"completion\": {\"field\": \"suggest\"}}}}")
    List<VehicleSearchDocument> findSuggestions(String prefix);

    /**
     * Similar vehicles based on make, model, and price range
     */
    @Query("{"
        + "\"bool\": {"
        + "  \"should\": ["
        + "    {\"term\": {\"make\": \"?0\"}},"
        + "    {\"term\": {\"model\": \"?1\"}},"
        + "    {\"range\": {\"price\": {\"gte\": ?2, \"lte\": ?3}}}"
        + "  ],"
        + "  \"minimum_should_match\": 1"
        + "}}")
    Page<VehicleSearchDocument> findSimilarVehicles(
        String make,
        String model, 
        BigDecimal priceMin,
        BigDecimal priceMax,
        Pageable pageable
    );

    /**
     * Popular vehicles based on views and inquiries
     */
    Page<VehicleSearchDocument> findByPopularityGreaterThanOrderByPopularityDesc(Integer minPopularity, Pageable pageable);

    /**
     * Recent vehicles
     */
    Page<VehicleSearchDocument> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    /**
     * Search with boost for featured and verified vehicles
     */
    @Query("{"
        + "\"function_score\": {"
        + "  \"query\": {"
        + "    \"multi_match\": {"
        + "      \"query\": \"?0\","
        + "      \"fields\": [\"make^3\", \"model^3\", \"searchableText^2\", \"description\"]"
        + "    }"
        + "  },"
        + "  \"functions\": ["
        + "    {"
        + "      \"filter\": {\"term\": {\"isFeatured\": true}},"
        + "      \"weight\": 2"
        + "    },"
        + "    {"
        + "      \"filter\": {\"term\": {\"isVerified\": true}},"
        + "      \"weight\": 1.5"
        + "    },"
        + "    {"
        + "      \"field_value_factor\": {"
        + "        \"field\": \"searchBoost\","
        + "        \"factor\": 1.2,"
        + "        \"missing\": 1"
        + "      }"
        + "    }"
        + "  ],"
        + "  \"score_mode\": \"multiply\","
        + "  \"boost_mode\": \"multiply\""
        + "}}")
    Page<VehicleSearchDocument> findWithBoostingSearch(String query, Pageable pageable);
}
