package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.Car;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * VehicleRepository - Alias for CarRepository to maintain naming consistency
 * This interface provides ML services with expected naming while delegating to
 * CarRepository
 */
@Repository
public interface VehicleRepository extends JpaRepository<Car, Long> {

       // Basic finder methods
       List<Car> findAll();

       // Find active vehicles (alias for ML service compatibility)
       @Query("SELECT c FROM Car c WHERE c.isActive = true")
       List<Car> findByIsActiveTrue();

       // Find by make and model (for ML)
       @Query("SELECT c FROM Car c WHERE c.make = :make AND c.model = :model AND c.isActive = true")
       List<Car> findByMakeAndModel(@Param("make") String make, @Param("model") String model);

       // Find by make, model and price range for content-based filtering
       @Query("SELECT c FROM Car c WHERE c.make IN :makes AND c.price BETWEEN :minPrice AND :maxPrice AND c.year >= :minYear AND c.isActive = true")
       List<Car> findByMakeInAndPriceBetweenAndYearGreaterThanEqual(@Param("makes") List<String> makes,
                     @Param("minPrice") BigDecimal minPrice,
                     @Param("maxPrice") BigDecimal maxPrice,
                     @Param("minYear") Integer minYear);

       // Find most popular vehicles (for trending)
       @Query(value = "SELECT c FROM Car c WHERE c.isActive = true ORDER BY c.viewCount DESC LIMIT :limit", nativeQuery = false)
       List<Car> findMostPopularVehicles(@Param("limit") int limit);

       // Find vehicles by date range (for trending analysis)
       List<Car> findByCreatedAtAfterOrUpdatedAtAfter(LocalDateTime createdAfter, LocalDateTime updatedAfter);

       List<Car> findByCreatedAtAfterAndYearGreaterThan(LocalDateTime createdAfter, Integer year);

       // Find similar vehicles based on attributes
       @Query("SELECT c FROM Car c WHERE c.make = :make AND c.model = :model AND " +
                     "c.price BETWEEN :minPrice AND :maxPrice AND c.year BETWEEN :minYear AND :maxYear AND " +
                     "c.isActive = true ORDER BY ABS(c.price - :targetPrice)")
       List<Car> findSimilarVehicles(@Param("make") String make,
                     @Param("model") String model,
                     @Param("minPrice") BigDecimal minPrice,
                     @Param("maxPrice") BigDecimal maxPrice,
                     @Param("minYear") Integer minYear,
                     @Param("maxYear") Integer maxYear,
                     @Param("targetPrice") BigDecimal targetPrice,
                     @Param("limit") int limit);

       // Find comparable vehicles for price prediction
       @Query("SELECT c FROM Car c WHERE c.make = :make AND c.model = :model AND " +
                     "c.year BETWEEN :minYear AND :maxYear AND c.mileage BETWEEN :minMileage AND :maxMileage AND " +
                     "c.isActive = true ORDER BY ABS(c.year - :targetYear) ASC")
       List<Car> findComparableVehicles(@Param("make") String make,
                     @Param("model") String model,
                     @Param("minYear") Integer minYear,
                     @Param("maxYear") Integer maxYear,
                     @Param("minMileage") Double minMileage,
                     @Param("maxMileage") Double maxMileage,
                     @Param("targetYear") Integer targetYear,
                     @Param("limit") int limit);

       // Find vehicles with high feature-to-price ratio
       @Query("SELECT c FROM Car c WHERE c.isActive = true ORDER BY (c.viewCount / c.price) DESC")
       List<Car> findVehiclesWithHighFeaturesToPriceRatio();

       // Find similar vehicles for trend analysis
       @Query("SELECT c FROM Car c WHERE c.make = :make AND c.model = :model AND " +
                     "c.year BETWEEN :minYear AND :maxYear AND c.isActive = true")
       List<Car> findSimilarVehiclesForTrends(@Param("make") String make,
                     @Param("model") String model,
                     @Param("minYear") Integer minYear,
                     @Param("maxYear") Integer maxYear);
}
