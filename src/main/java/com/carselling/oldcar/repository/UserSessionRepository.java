package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.UserSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for UserSession
 */
@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, String> {

    Optional<UserSession> findBySessionId(String sessionId);

    Page<UserSession> findByUserId(Long userId, Pageable pageable);

    List<UserSession> findByUserIdAndIsActiveTrue(Long userId);

    /**
     * Get active session count
     */
    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.isActive = true")
    long countActiveSessions();

    /**
     * Get sessions started in date range
     */
    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.startedAt BETWEEN :startDate AND :endDate")
    long countSessionsInRange(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get average session duration
     */
    @Query("SELECT AVG(s.durationSeconds) FROM UserSession s " +
            "WHERE s.startedAt BETWEEN :startDate AND :endDate AND s.durationSeconds IS NOT NULL")
    Double getAverageSessionDuration(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get session count by device type
     */
    @Query("SELECT s.deviceType, COUNT(s) FROM UserSession s " +
            "WHERE s.startedAt BETWEEN :startDate AND :endDate AND s.deviceType IS NOT NULL " +
            "GROUP BY s.deviceType")
    List<Object[]> getSessionsByDevice(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Expire stale sessions (no activity for 30 minutes)
     */
    @Modifying
    @Query("UPDATE UserSession s SET s.isActive = false, s.endedAt = CURRENT_TIMESTAMP " +
            "WHERE s.isActive = true AND s.startedAt < :cutoff")
    int expireStaleSessions(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Delete old sessions for retention
     */
    @Modifying
    @Query("DELETE FROM UserSession s WHERE s.startedAt < :cutoffDate")
    int deleteOldSessions(@Param("cutoffDate") LocalDateTime cutoffDate);
}
