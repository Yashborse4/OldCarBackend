package com.carselling.oldcar.search;

import com.carselling.oldcar.document.VehicleSearchDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;


import java.math.BigDecimal;
import java.util.List;

public interface VehicleSearchRepository extends ElasticsearchRepository<VehicleSearchDocument, String> {

    /**
      * SECURITY MODEL:
      * 
      * Public methods (for general users):
      * - Methods ending with "AndActiveTrueAndDealerVerifiedTrue" enforce strict visibility rules
      * - Only return active cars from verified dealers
      * - Should be used for all public-facing search endpoints
      * 
      * Admin methods (for internal/moderation use):
      * - Methods ending with "AndActiveTrue" bypass dealer verification check
      * - Still enforce active status for safety
      * - Should be used only in admin/moderation contexts
      * 
      * Management methods (for full access):
      * - Methods without security suffixes provide full access
      * - Include inactive cars, all dealer statuses
      * - Should be used only for system management tasks
      * 
      * Always choose the appropriate method based on the security context of your use case.
      */

    // Public visibility methods - enforce active and verified dealer
    Page<VehicleSearchDocument> findByActiveTrueAndDealerVerifiedTrueOrderByCreatedAtDesc(Pageable pageable);

    Page<VehicleSearchDocument> findByActiveTrueAndDealerVerifiedTrue(Pageable pageable);

    Page<VehicleSearchDocument> findByBrandAndModelAndActiveTrueAndDealerVerifiedTrue(String brand, String model, Pageable pageable);

    Page<VehicleSearchDocument> findByBrandAndActiveTrueAndDealerVerifiedTrue(String brand, Pageable pageable);

    Page<VehicleSearchDocument> findByBrandAndModelAndPriceBetweenAndActiveTrueAndDealerVerifiedTrue(String brand,
                                                                                                    String model,
                                                                                                    BigDecimal minPrice,
                                                                                                    BigDecimal maxPrice,
                                                                                                    Pageable pageable);

    // Admin/secure methods - bypass visibility restrictions but still respect active status
    Page<VehicleSearchDocument> findByActiveTrueOrderByCreatedAtDesc(Pageable pageable);

    Page<VehicleSearchDocument> findByBrandAndModelAndActiveTrue(String brand, String model, Pageable pageable);

    Page<VehicleSearchDocument> findByBrandAndActiveTrue(String brand, Pageable pageable);

    Page<VehicleSearchDocument> findByBrandAndModelAndPriceBetweenAndActiveTrue(String brand,
                                                                                String model,
                                                                                BigDecimal minPrice,
                                                                                BigDecimal maxPrice,
                                                                                Pageable pageable);

    // Admin methods - access all documents including inactive ones (for moderation/management)
    Page<VehicleSearchDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<VehicleSearchDocument> findByBrandAndModel(String brand, String model, Pageable pageable);

    Page<VehicleSearchDocument> findByBrand(String brand, Pageable pageable);

    Page<VehicleSearchDocument> findByBrandAndModelAndPriceBetween(String brand,
                                                                   String model,
                                                                   BigDecimal minPrice,
                                                                   BigDecimal maxPrice,
                                                                   Pageable pageable);

    // Search methods with security constraints
    Page<VehicleSearchDocument> findWithBoostingSearchAndFilters(String query, List<String> brands, List<String> models, Pageable pageable);

    Page<VehicleSearchDocument> findWithBoostingSearchAndActiveTrueAndDealerVerifiedTrue(String query, Pageable pageable);

    Page<VehicleSearchDocument> findWithBoostingSearch(String query, Pageable pageable);

    List<VehicleSearchDocument> findSuggestions(String prefix);

    Page<VehicleSearchDocument> findSimilarVehicles(String brand,
                                                    String model,
                                                    BigDecimal minPrice,
                                                    BigDecimal maxPrice,
                                                    Pageable pageable);
}
