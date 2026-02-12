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
        List<Object[]> getDailyEventCountsByCarId(@Param("carId") Long carId,
                        @Param("startDate") LocalDateTime startDate);

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

        /**
         * Count unique viewers for a car
         */
        @Query("SELECT COUNT(DISTINCT e.user.id) FROM CarInteractionEvent e WHERE e.car.id = :carId AND e.eventType = 'CAR_VIEW' AND e.user IS NOT NULL")
        long countUniqueViewersByCarId(@Param("carId") Long carId);

        /**
         * Get daily view counts for a car
         */
        /**
         * Get daily view counts for a car
         */
        @Query("SELECT FUNCTION('DATE', e.createdAt) as date, COUNT(e) as count FROM CarInteractionEvent e WHERE e.car.id = :carId AND e.eventType = 'CAR_VIEW' AND e.createdAt >= :startDate GROUP BY FUNCTION('DATE', e.createdAt) ORDER BY date")
        List<Object[]> getDailyViewCountsByCarId(@Param("carId") Long carId,
                        @Param("startDate") LocalDateTime startDate);

        // =============== DEALER INSIGHTS ===============

        /**
         * Get total engagement for dealer's cars (aggregated by event type)
         */
        @Query("SELECT e.eventType, COUNT(e) FROM CarInteractionEvent e WHERE e.car.id IN :carIds GROUP BY e.eventType")
        List<Object[]> getDealerTotalEngagement(@Param("carIds") List<Long> carIds);

        /**
         * Get monthly stats for dealer's cars (Views)
         */
        @Query("SELECT FUNCTION('MONTHNAME', e.createdAt) as month, COUNT(e) as views FROM CarInteractionEvent e WHERE e.car.id IN :carIds AND e.eventType = 'CAR_VIEW' AND e.createdAt >= :startDate GROUP BY FUNCTION('MONTHNAME', e.createdAt), FUNCTION('MONTH', e.createdAt) ORDER BY FUNCTION('MONTH', e.createdAt)")
        List<Object[]> getDealerMonthlyViews(@Param("carIds") List<Long> carIds,
                        @Param("startDate") LocalDateTime startDate);

        /**
         * Get monthly stats for dealer's cars (Inquiries)
         */
        @Query("SELECT FUNCTION('MONTHNAME', e.createdAt) as month, COUNT(e) as inquiries FROM CarInteractionEvent e WHERE e.car.id IN :carIds AND e.eventType IN ('CONTACT_CLICK', 'CALL_CLICK', 'CHAT_OPEN', 'WHATSAPP_CLICK') AND e.createdAt >= :startDate GROUP BY FUNCTION('MONTHNAME', e.createdAt), FUNCTION('MONTH', e.createdAt) ORDER BY FUNCTION('MONTH', e.createdAt)")
        List<Object[]> getDealerMonthlyInquiries(@Param("carIds") List<Long> carIds,
                        @Param("startDate") LocalDateTime startDate);

        /**
         * Get top performed car IDs (most views)
         */
        @Query("SELECT e.car.id FROM CarInteractionEvent e WHERE e.car.id IN :carIds AND e.eventType = 'CAR_VIEW' GROUP BY e.car.id ORDER BY COUNT(e) DESC")
        List<Long> findTopPerformedCarIds(@Param("carIds") List<Long> carIds, Pageable pageable);

        /**
         * Get location stats regarding views (using user city if available, or just
         * grouping by null if not)
         * Note: CarInteractionEvent might not have City directly unless added.
         * We will assume we can't easily get location stats yet or we group by
         * e.user.city if user exists?
         * For now, let's return empty or skipped, OR if metadata has it.
         * Let's assume we skip location stats for now or return generic.
         * Actually, let's group by e.ipAddress as a proxy? No.
         * Let's skip location stats in the repository for now or return an empty list
         * compatible signature if needed.
         * But UserAnalyticsService expects it.
         * Let's add a placeholder returning empty list or implement based on
         * e.user.city?
         * "SELECT e.user.city, COUNT(e) ..."
         */
        @Query("SELECT e.user.city, COUNT(e) FROM CarInteractionEvent e WHERE e.car.id IN :carIds AND e.eventType = 'CAR_VIEW' AND e.user.city IS NOT NULL GROUP BY e.user.city ORDER BY COUNT(e) DESC")
        List<Object[]> getDealerLocationStats(@Param("carIds") List<Long> carIds);

        /**
         * Get user view counts for dealer's cars (for Lead Generation)
         */
        @Query("SELECT e.user.id, e.car.id, COUNT(e) FROM CarInteractionEvent e WHERE e.car.id IN :carIds AND e.eventType = 'CAR_VIEW' AND e.user IS NOT NULL GROUP BY e.user.id, e.car.id ORDER BY COUNT(e) DESC")
        org.springframework.data.domain.Slice<Object[]> getUserViewCountsForCars(@Param("carIds") List<Long> carIds,
                        Pageable pageable);
}
