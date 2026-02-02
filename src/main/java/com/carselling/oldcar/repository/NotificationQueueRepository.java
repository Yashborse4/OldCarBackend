package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.NotificationQueue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationQueueRepository extends JpaRepository<NotificationQueue, Long> {

    List<NotificationQueue> findByStatusAndNextRetryAtLessThanEqual(
            NotificationQueue.NotificationStatus status, LocalDateTime now);

    /**
     * Find pending notifications with pagination for batch processing.
     * Orders by next_retry_at to process oldest retries first.
     */
    @Query("SELECT n FROM NotificationQueue n WHERE n.status = :status AND n.nextRetryAt <= :now ORDER BY n.nextRetryAt ASC")
    List<NotificationQueue> findPendingNotifications(
            @Param("status") NotificationQueue.NotificationStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    long countByStatus(NotificationQueue.NotificationStatus status);
}
