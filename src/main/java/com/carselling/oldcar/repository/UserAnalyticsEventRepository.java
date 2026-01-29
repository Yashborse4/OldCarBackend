package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.UserAnalyticsEvent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for UserAnalyticsEvent with aggregation queries
 */
@Repository
public interface UserAnalyticsEventRepository extends JpaRepository<UserAnalyticsEvent, Long> {

        // =============== BASIC QUERIES ===============

        Page<UserAnalyticsEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

        Page<UserAnalyticsEvent> findBySessionId(String sessionId, Pageable pageable);

        List<UserAnalyticsEvent> findBySessionIdOrderByCreatedAtAsc(String sessionId);

        // =============== CAR INSIGHTS ===============

        /**
         * Count events by type for a specific car
         */
        @Query("SELECT e.eventType, COUNT(e) FROM UserAnalyticsEvent e " +
                        "WHERE e.targetType = 'CAR' AND e.targetId = :carId " +
                        "GROUP BY e.eventType")
        List<Object[]> getCarEventCounts(@Param("carId") String carId);

        /**
         * Get total views for a car
         */
        @Query("SELECT COUNT(e) FROM UserAnalyticsEvent e " +
                        "WHERE e.targetType = 'CAR' AND e.targetId = :carId AND e.eventType = 'CAR_VIEW'")
        long countCarViews(@Param("carId") String carId);

        /**
         * Get unique viewers for a car
         */
        @Query("SELECT COUNT(DISTINCT e.userId) FROM UserAnalyticsEvent e " +
                        "WHERE e.targetType = 'CAR' AND e.targetId = :carId AND e.eventType = 'CAR_VIEW' AND e.userId IS NOT NULL")
        long countUniqueCarViewers(@Param("carId") String carId);

        /**
         * Get average view duration for a car
         */
        @Query("SELECT AVG(e.actionDurationSeconds) FROM UserAnalyticsEvent e " +
                        "WHERE e.targetType = 'CAR' AND e.targetId = :carId AND e.eventType = 'CAR_VIEW_DURATION'")
        Double getAverageCarViewDuration(@Param("carId") String carId);

        /**
         * Get daily car views for trend analysis
         */
        @Query("SELECT FUNCTION('DATE', e.createdAt) as date, COUNT(e) as count " +
                        "FROM UserAnalyticsEvent e " +
                        "WHERE e.targetType = 'CAR' AND e.targetId = :carId AND e.eventType = 'CAR_VIEW' " +
                        "AND e.createdAt >= :startDate " +
                        "GROUP BY FUNCTION('DATE', e.createdAt) ORDER BY date")
        List<Object[]> getDailyCarViews(@Param("carId") String carId, @Param("startDate") LocalDateTime startDate);

        // =============== DEALER INSIGHTS ===============

        /**
         * Get event counts for all cars owned by a dealer
         */
        @Query("SELECT e.targetId, e.eventType, COUNT(e) FROM UserAnalyticsEvent e " +
                        "WHERE e.targetType = 'CAR' AND e.targetId IN :carIds " +
                        "GROUP BY e.targetId, e.eventType")
        List<Object[]> getDealerCarEvents(@Param("carIds") List<String> carIds);

        /**
         * Get total engagement for dealer's cars
         */
        @Query("SELECT e.eventType, COUNT(e) FROM UserAnalyticsEvent e " +
                        "WHERE e.targetType = 'CAR' AND e.targetId IN :carIds " +
                        "GROUP BY e.eventType")
        List<Object[]> getDealerTotalEngagement(@Param("carIds") List<String> carIds);

        // =============== PLATFORM INSIGHTS ===============

        /**
         * Get most viewed cars in date range
         */
        @Query("SELECT e.targetId, COUNT(e) as views FROM UserAnalyticsEvent e " +
                        "WHERE e.targetType = 'CAR' AND e.eventType = 'CAR_VIEW' " +
                        "AND e.createdAt BETWEEN :startDate AND :endDate " +
                        "GROUP BY e.targetId ORDER BY views DESC")
        List<Object[]> getMostViewedCars(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        Pageable pageable);

        /**
         * Get event counts by type for date range
         */
        @Query("SELECT e.eventType, COUNT(e) FROM UserAnalyticsEvent e " +
                        "WHERE e.createdAt BETWEEN :startDate AND :endDate " +
                        "GROUP BY e.eventType")
        List<Object[]> getEventTypeCounts(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        /**
         * Get hourly event distribution
         */
        @Query("SELECT FUNCTION('HOUR', e.createdAt) as hour, COUNT(e) as count " +
                        "FROM UserAnalyticsEvent e " +
                        "WHERE e.createdAt BETWEEN :startDate AND :endDate " +
                        "GROUP BY FUNCTION('HOUR', e.createdAt) ORDER BY hour")
        List<Object[]> getHourlyDistribution(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        /**
         * Get device type distribution
         */
        @Query("SELECT e.deviceType, COUNT(e) FROM UserAnalyticsEvent e " +
                        "WHERE e.createdAt BETWEEN :startDate AND :endDate AND e.deviceType IS NOT NULL " +
                        "GROUP BY e.deviceType")
        List<Object[]> getDeviceDistribution(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        /**
         * Get city distribution
         */
        @Query("SELECT e.city, COUNT(e) FROM UserAnalyticsEvent e " +
                        "WHERE e.createdAt BETWEEN :startDate AND :endDate AND e.city IS NOT NULL " +
                        "GROUP BY e.city ORDER BY COUNT(e) DESC")
        List<Object[]> getCityDistribution(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate,
                        Pageable pageable);

        // =============== DEALER INSIGHTS ===============

        /**
         * Get monthly stats for dealer's cars (Views)
         */
        @Query("SELECT FUNCTION('MONTHNAME', e.createdAt) as month, COUNT(e) as views " +
                        "FROM UserAnalyticsEvent e " +
                        "WHERE e.targetType = 'CAR' AND e.targetId IN :carIds AND e.eventType = 'CAR_VIEW' " +
                        "AND e.createdAt >= :startDate " +
                        "GROUP BY FUNCTION('MONTHNAME', e.createdAt), FUNCTION('MONTH', e.createdAt) " +
                        "ORDER BY FUNCTION('MONTH', e.createdAt)")
        List<Object[]> getDealerMonthlyViews(@Param("carIds") List<String> carIds,
                        @Param("startDate") LocalDateTime startDate);

        /**
         * Get monthly stats for dealer's cars (Inquiries)
         */
        @Query("SELECT FUNCTION('MONTHNAME', e.createdAt) as month, COUNT(e) as inquiries " +
                        "FROM UserAnalyticsEvent e " +
                        "WHERE e.targetType = 'CAR' AND e.targetId IN :carIds " +
                        "AND e.eventType IN ('CAR_CONTACT_CLICK', 'CAR_CALL_CLICK', 'CAR_CHAT_OPEN', 'CAR_WHATSAPP_CLICK') "
                        +
                        "AND e.createdAt >= :startDate " +
                        "GROUP BY FUNCTION('MONTHNAME', e.createdAt), FUNCTION('MONTH', e.createdAt) " +
                        "ORDER BY FUNCTION('MONTH', e.createdAt)")
        List<Object[]> getDealerMonthlyInquiries(@Param("carIds") List<String> carIds,
                        @Param("startDate") LocalDateTime startDate);

        /**
         * Get top performed car IDs (most views)
         */
        @Query("SELECT e.targetId FROM UserAnalyticsEvent e " +
                        "WHERE e.targetType = 'CAR' AND e.eventType = 'CAR_VIEW' " +
                        "AND e.targetId IN :carIds " +
                        "GROUP BY e.targetId " +
                        "ORDER BY COUNT(e) DESC")
        List<String> findTopPerformedCarIds(@Param("carIds") List<String> carIds, Pageable pageable);

        /**
         * Get location stats regarding views
         * Assuming Location is derived from City metadata in events for now as we don't
         * have Car Location in Events table directly unless we join,
         * BUT easier: aggregate by Event City.
         */
        @Query("SELECT e.city, COUNT(e) FROM UserAnalyticsEvent e " +
                        "WHERE e.targetType = 'CAR' AND e.targetId IN :carIds AND e.eventType = 'CAR_VIEW' " +
                        "AND e.city IS NOT NULL " +
                        "GROUP BY e.city ORDER BY COUNT(e) DESC")
        List<Object[]> getDealerLocationStats(@Param("carIds") List<String> carIds);

        // =============== SCREEN ANALYTICS ===============

        /**
         * Get screen view counts
         */
        @Query("SELECT e.screenName, COUNT(e) FROM UserAnalyticsEvent e " +
                        "WHERE e.eventType = 'SCREEN_VIEW' AND e.createdAt BETWEEN :startDate AND :endDate " +
                        "GROUP BY e.screenName ORDER BY COUNT(e) DESC")
        List<Object[]> getScreenViewCounts(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        /**
         * Get screen exit counts (drop-off analysis)
         */
        @Query("SELECT e.screenName, COUNT(e) FROM UserAnalyticsEvent e " +
                        "WHERE e.eventType = 'SCREEN_EXIT' AND e.createdAt BETWEEN :startDate AND :endDate " +
                        "GROUP BY e.screenName ORDER BY COUNT(e) DESC")
        List<Object[]> getScreenExitCounts(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        // =============== USER BEHAVIOR ===============

        /**
         * Get user's recent activity
         */
        Page<UserAnalyticsEvent> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
                        Long userId, LocalDateTime after, Pageable pageable);

        /**
         * Count active users in date range
         */
        @Query("SELECT COUNT(DISTINCT e.userId) FROM UserAnalyticsEvent e " +
                        "WHERE e.createdAt BETWEEN :startDate AND :endDate AND e.userId IS NOT NULL")
        long countActiveUsers(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        // =============== RETENTION ===============

        /**
         * Delete old events for retention policy
         */
        @Modifying
        @Query("DELETE FROM UserAnalyticsEvent e WHERE e.createdAt < :cutoffDate")
        int deleteOldEvents(@Param("cutoffDate") LocalDateTime cutoffDate);
}
