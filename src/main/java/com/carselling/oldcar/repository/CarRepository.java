package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Car Repository with advanced search and filtering capabilities
 * Provides efficient data access methods for car management
 */
@Repository
public interface CarRepository extends JpaRepository<Car, Long> {

    // Basic finder methods
    List<Car> findByOwner(User owner);
    
    Page<Car> findByOwner(User owner, Pageable pageable);
    
    List<Car> findByOwnerId(Long ownerId);
    
    Page<Car> findByOwnerId(Long ownerId, Pageable pageable);

    // Find active cars
    @Query("SELECT c FROM Car c WHERE c.isActive = true")
    List<Car> findAllActiveCars();
    
    @Query("SELECT c FROM Car c WHERE c.isActive = true")
    Page<Car> findAllActiveCars(Pageable pageable);

    // Find active cars by owner
    @Query("SELECT c FROM Car c WHERE c.owner = :owner AND c.isActive = true")
    List<Car> findActiveCarsByOwner(@Param("owner") User owner);
    
    @Query("SELECT c FROM Car c WHERE c.owner = :owner AND c.isActive = true")
    Page<Car> findActiveCarsByOwner(@Param("owner") User owner, Pageable pageable);

    @Query("SELECT c FROM Car c WHERE c.owner.id = :ownerId AND c.isActive = true")
    List<Car> findActiveCarsByOwnerId(@Param("ownerId") Long ownerId);
    
    @Query("SELECT c FROM Car c WHERE c.owner.id = :ownerId AND c.isActive = true")
    Page<Car> findActiveCarsByOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

    // Find cars by make and model
    List<Car> findByMakeIgnoreCase(String make);
    
    Page<Car> findByMakeIgnoreCase(String make, Pageable pageable);
    
    List<Car> findByModelIgnoreCase(String model);
    
    Page<Car> findByModelIgnoreCase(String model, Pageable pageable);
    
    List<Car> findByMakeIgnoreCaseAndModelIgnoreCase(String make, String model);
    
    Page<Car> findByMakeIgnoreCaseAndModelIgnoreCase(String make, String model, Pageable pageable);

    // Find cars by year range
    List<Car> findByYearBetween(Integer startYear, Integer endYear);
    
    Page<Car> findByYearBetween(Integer startYear, Integer endYear, Pageable pageable);

    // Find cars by price range
    List<Car> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice);
    
    Page<Car> findByPriceBetween(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    // Find featured cars
    @Query("SELECT c FROM Car c WHERE c.isFeatured = true AND c.isActive = true AND " +
           "(c.featuredUntil IS NULL OR c.featuredUntil > CURRENT_TIMESTAMP)")
    List<Car> findCurrentlyFeaturedCars();
    
    @Query("SELECT c FROM Car c WHERE c.isFeatured = true AND c.isActive = true AND " +
           "(c.featuredUntil IS NULL OR c.featuredUntil > CURRENT_TIMESTAMP)")
    Page<Car> findCurrentlyFeaturedCars(Pageable pageable);

    // Find cars by owner role
    @Query("SELECT c FROM Car c WHERE c.owner.role = :role AND c.isActive = true")
    List<Car> findCarsByOwnerRole(@Param("role") Role role);
    
    @Query("SELECT c FROM Car c WHERE c.owner.role = :role AND c.isActive = true")
    Page<Car> findCarsByOwnerRole(@Param("role") Role role, Pageable pageable);

    // Find sold/unsold cars
    List<Car> findByIsSold(Boolean isSold);
    
    Page<Car> findByIsSold(Boolean isSold, Pageable pageable);

    // Advanced search with multiple criteria
    @Query("SELECT c FROM Car c WHERE " +
           "(:make IS NULL OR LOWER(c.make) LIKE LOWER(CONCAT('%', :make, '%'))) AND " +
           "(:model IS NULL OR LOWER(c.model) LIKE LOWER(CONCAT('%', :model, '%'))) AND " +
           "(:minYear IS NULL OR c.year >= :minYear) AND " +
           "(:maxYear IS NULL OR c.year <= :maxYear) AND " +
           "(:minPrice IS NULL OR c.price >= :minPrice) AND " +
           "(:maxPrice IS NULL OR c.price <= :maxPrice) AND " +
           "(:ownerRole IS NULL OR c.owner.role = :ownerRole) AND " +
           "(:isFeatured IS NULL OR c.isFeatured = :isFeatured) AND " +
           "(:isSold IS NULL OR c.isSold = :isSold) AND " +
           "c.isActive = true")
    Page<Car> findCarsByCriteria(@Param("make") String make,
                                @Param("model") String model,
                                @Param("minYear") Integer minYear,
                                @Param("maxYear") Integer maxYear,
                                @Param("minPrice") BigDecimal minPrice,
                                @Param("maxPrice") BigDecimal maxPrice,
                                @Param("ownerRole") Role ownerRole,
                                @Param("isFeatured") Boolean isFeatured,
                                @Param("isSold") Boolean isSold,
                                Pageable pageable);

    // Text search in description
    @Query("SELECT c FROM Car c WHERE " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND " +
           "c.isActive = true")
    List<Car> findCarsByDescriptionContaining(@Param("searchTerm") String searchTerm);
    
    @Query("SELECT c FROM Car c WHERE " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND " +
           "c.isActive = true")
    Page<Car> findCarsByDescriptionContaining(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Full text search across multiple fields
    @Query("SELECT c FROM Car c WHERE " +
           "(LOWER(c.make) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.model) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "c.isActive = true")
    List<Car> searchCars(@Param("searchTerm") String searchTerm);
    
    @Query("SELECT c FROM Car c WHERE " +
           "(LOWER(c.make) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.model) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) AND " +
           "c.isActive = true")
    Page<Car> searchCars(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Find cars created within date range
    @Query("SELECT c FROM Car c WHERE c.createdAt BETWEEN :startDate AND :endDate AND c.isActive = true")
    List<Car> findCarsCreatedBetween(@Param("startDate") LocalDateTime startDate, 
                                    @Param("endDate") LocalDateTime endDate);

    @Query("SELECT c FROM Car c WHERE c.createdAt BETWEEN :startDate AND :endDate AND c.isActive = true")
    Page<Car> findCarsCreatedBetween(@Param("startDate") LocalDateTime startDate, 
                                    @Param("endDate") LocalDateTime endDate, 
                                    Pageable pageable);

    // Find cars by additional attributes
    List<Car> findByFuelTypeIgnoreCase(String fuelType);
    
    List<Car> findByTransmissionIgnoreCase(String transmission);
    
    List<Car> findByColorIgnoreCase(String color);
    
    @Query("SELECT c FROM Car c WHERE c.mileage BETWEEN :minMileage AND :maxMileage AND c.isActive = true")
    List<Car> findCarsByMileageRange(@Param("minMileage") Integer minMileage, 
                                    @Param("maxMileage") Integer maxMileage);

    // Find cars with high view counts (popular cars)
    @Query("SELECT c FROM Car c WHERE c.viewCount >= :minViews AND c.isActive = true ORDER BY c.viewCount DESC")
    List<Car> findPopularCars(@Param("minViews") Long minViews);
    
    @Query("SELECT c FROM Car c WHERE c.viewCount >= :minViews AND c.isActive = true ORDER BY c.viewCount DESC")
    Page<Car> findPopularCars(@Param("minViews") Long minViews, Pageable pageable);

    // Count methods for statistics
    long countByIsActive(Boolean isActive);
    
    long countByOwner(User owner);
    
    long countByOwnerRole(@Param("role") Role role);
    
    @Query("SELECT COUNT(c) FROM Car c WHERE c.createdAt >= :date AND c.isActive = true")
    long countCarsCreatedSince(@Param("date") LocalDateTime date);
    
    @Query("SELECT COUNT(c) FROM Car c WHERE c.isFeatured = true AND c.isActive = true")
    long countFeaturedCars();

    // Update methods
    @Modifying
    @Query("UPDATE Car c SET c.viewCount = c.viewCount + 1 WHERE c.id = :carId")
    int incrementViewCount(@Param("carId") Long carId);

    @Modifying
    @Query("UPDATE Car c SET c.isFeatured = :featured, c.featuredUntil = :featuredUntil WHERE c.id = :carId")
    int updateFeatureStatus(@Param("carId") Long carId, 
                           @Param("featured") Boolean featured, 
                           @Param("featuredUntil") LocalDateTime featuredUntil);

    @Modifying
    @Query("UPDATE Car c SET c.isSold = :isSold WHERE c.id = :carId")
    int updateSoldStatus(@Param("carId") Long carId, @Param("isSold") Boolean isSold);

    @Modifying
    @Query("UPDATE Car c SET c.isActive = :isActive WHERE c.id = :carId")
    int updateActiveStatus(@Param("carId") Long carId, @Param("isActive") Boolean isActive);

    // Soft delete (set isActive to false)
    @Modifying
    @Query("UPDATE Car c SET c.isActive = false WHERE c.id = :carId")
    int softDeleteCar(@Param("carId") Long carId);

    @Modifying
    @Query("UPDATE Car c SET c.isActive = false WHERE c.owner.id = :ownerId")
    int softDeleteCarsByOwner(@Param("ownerId") Long ownerId);

    // Cleanup methods
    @Modifying
    @Query("DELETE FROM Car c WHERE c.isActive = false AND c.updatedAt < :cutoffDate")
    int deleteSoftDeletedCarsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("UPDATE Car c SET c.isFeatured = false, c.featuredUntil = null WHERE c.featuredUntil < CURRENT_TIMESTAMP")
    int expireFeaturedCars();

    // Statistics queries for admin dashboard
    @Query("SELECT c.make, COUNT(c) FROM Car c WHERE c.isActive = true GROUP BY c.make ORDER BY COUNT(c) DESC")
    List<Object[]> getCarCountByMake();

    @Query("SELECT c.owner.role, COUNT(c) FROM Car c WHERE c.isActive = true GROUP BY c.owner.role")
    List<Object[]> getCarCountByOwnerRole();

    @Query("SELECT DATE(c.createdAt), COUNT(c) FROM Car c " +
           "WHERE c.createdAt >= :startDate AND c.isActive = true " +
           "GROUP BY DATE(c.createdAt) " +
           "ORDER BY DATE(c.createdAt)")
    List<Object[]> getCarCreationStats(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT AVG(c.price) FROM Car c WHERE c.isActive = true")
    BigDecimal getAverageCarPrice();

    @Query("SELECT c.make, AVG(c.price) FROM Car c WHERE c.isActive = true GROUP BY c.make")
    List<Object[]> getAveragePriceByMake();

    // Recent cars
    @Query("SELECT c FROM Car c WHERE c.isActive = true ORDER BY c.createdAt DESC")
    Page<Car> findRecentCars(Pageable pageable);

    // Most viewed cars
    @Query("SELECT c FROM Car c WHERE c.isActive = true ORDER BY c.viewCount DESC")
    Page<Car> findMostViewedCars(Pageable pageable);
    
    // ML-specific methods for recommendation engine
    
    // Find by isActive = true (alias for ML service)
    @Query("SELECT c FROM Car c WHERE c.isActive = true")
    List<Car> findByIsActiveTrue();
    
    // Find by make and model (for ML)
    List<Car> findByMakeAndModel(String make, String model);
    
    // Find by make, model and price range for content-based filtering
    @Query("SELECT c FROM Car c WHERE c.make IN :makes AND c.price BETWEEN :minPrice AND :maxPrice AND c.year >= :minYear AND c.isActive = true")
    List<Car> findByMakeInAndPriceBetweenAndYearGreaterThanEqual(@Param("makes") List<String> makes, 
                                                                @Param("minPrice") BigDecimal minPrice, 
                                                                @Param("maxPrice") BigDecimal maxPrice, 
                                                                @Param("minYear") Integer minYear);
    
    // Find most popular vehicles (for trending)
    @Query("SELECT c FROM Car c WHERE c.isActive = true ORDER BY c.viewCount DESC")
    List<Car> findMostPopularVehicles(int limit);
    
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
