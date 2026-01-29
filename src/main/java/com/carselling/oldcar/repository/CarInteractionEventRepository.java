package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.CarInteractionEvent;
import com.carselling.oldcar.model.CarInteractionEvent.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;


/**
 * Repository for CarInteractionEvent entity
 */
@Repository
public interface CarInteractionEventRepository extends JpaRepository<CarInteractionEvent, Long> {

    /**
     * Find events by car ID
     */
    Page<CarInteractionEvent> findByCarId(Long carId, Pageable pageable);

    /**
     * Find events by user ID
     */
    Page<CarInteractionEvent> findByUserId(Long userId, Pageable pageable);

    /**
     * Count events by type for a car
     */
    long countByCarIdAndEventType(Long carId, EventType eventType);

    /**
     * Count total views for a car
     */
    @Query("SELECT COUNT(e) FROM CarInteractionEvent e WHERE e.car.id = :carId AND e.eventType = 'CAR_VIEW'")
    long countViewsByCarId(@Param("carId") Long carId);

    /**
     * Count events by type within date range
     */
    @Query("SELECT COUNT(e) FROM CarInteractionEvent e WHERE e.car.id = :carId AND e.eventType = :eventType AND e.createdAt BETWEEN :startDate AND :endDate")
    long countByCarIdAndEventTypeAndDateRange(
            @Param("carId") Long carId,
            @Param("eventType") EventType eventType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get event counts by type for a car
     */
    @Query("SELECT e.eventType as eventType, COUNT(e) as count FROM CarInteractionEvent e WHERE e.car.id = :carId GROUP BY e.eventType")
    List<Object[]> getEventCountsByCarId(@Param("carId") Long carId);

    /**
     * Get daily event counts for a car
     */
    @Query("SELECT FUNCTION('DATE', e.createdAt) as date, COUNT(e) as count FROM CarInteractionEvent e WHERE e.car.id = :carId AND e.createdAt >= :startDate GROUP BY FUNCTION('DATE', e.createdAt) ORDER BY date")
    List<Object[]> getDailyEventCountsByCarId(@Param("carId") Long carId, @Param("startDate") LocalDateTime startDate);

    /**
     * Get total events for cars owned by a dealer
     */
    @Query("SELECT e.car.id as carId, e.eventType as eventType, COUNT(e) as count FROM CarInteractionEvent e WHERE e.car.owner.id = :dealerId GROUP BY e.car.id, e.eventType")
    List<Object[]> getEventCountsByDealerId(@Param("dealerId") Long dealerId);

    /**
     * Check if user already viewed a car today (prevent duplicate view counts)
     */
    @Query("SELECT COUNT(e) > 0 FROM CarInteractionEvent e WHERE e.car.id = :carId AND e.user.id = :userId AND e.eventType = 'CAR_VIEW' AND e.createdAt >= :todayStart")
    boolean existsViewByCarIdAndUserIdToday(@Param("carId") Long carId, @Param("userId") Long userId,
            @Param("todayStart") LocalDateTime todayStart);

    /**
     * Check if a specific event already exists for a user and car
     */
    @Query("SELECT COUNT(e) > 0 FROM CarInteractionEvent e WHERE e.car.id = :carId AND e.user.id = :userId AND e.eventType = :eventType")
    boolean existsByCarIdAndUserIdAndEventType(@Param("carId") Long carId,
            @Param("userId") Long userId,
            @Param("eventType") EventType eventType);
}
