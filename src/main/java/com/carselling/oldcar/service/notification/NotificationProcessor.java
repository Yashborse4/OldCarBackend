package com.carselling.oldcar.service.notification;

import com.carselling.oldcar.model.NotificationQueue;
import com.carselling.oldcar.repository.NotificationQueueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProcessor {

    private final NotificationQueueRepository queueRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // Prevent concurrent processing
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    @Value("${app.notification.processor.batch-size:50}")
    private int batchSize;

    @Value("${app.notification.processor.max-retries:3}")
    private int maxRetries;

    /**
     * Process pending notifications every 5 minutes.
     * Uses a lock to prevent concurrent execution.
     */
    @Scheduled(fixedDelay = 300_000) // Run every 5 minutes
    public void processQueue() {
        // Prevent concurrent execution
        if (!isProcessing.compareAndSet(false, true)) {
            log.debug("Notification processor already running, skipping this cycle");
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now();

            // Fetch only a limited batch of notifications
            List<NotificationQueue> pendingNotifications = queueRepository.findPendingNotifications(
                    NotificationQueue.NotificationStatus.PENDING,
                    now,
                    PageRequest.of(0, batchSize));

            if (pendingNotifications.isEmpty()) {
                // No need to log anything if the queue is empty - this is normal operation
                return;
            }

            log.info("Processing {} pending notifications", pendingNotifications.size());

            int successCount = 0;
            int failCount = 0;

            for (NotificationQueue notification : pendingNotifications) {
                try {
                    boolean success = processNotification(notification);
                    if (success) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    log.error("Error processing notification {}: {}", notification.getId(), e.getMessage());
                    failCount++;
                }
            }

            if (successCount > 0 || failCount > 0) {
                log.info("Notification processing complete: {} sent, {} failed", successCount, failCount);
            }
        } finally {
            isProcessing.set(false);
        }
    }

    @Transactional
    protected boolean processNotification(NotificationQueue notification) {
        try {
            // Parse metadata
            Map<String, String> data = null;
            if (notification.getMetadata() != null && !notification.getMetadata().isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, String> parsedData = objectMapper.readValue(notification.getMetadata(), Map.class);
                    data = parsedData;
                } catch (Exception e) {
                    log.warn("Failed to parse metadata for notification {}", notification.getId());
                }
            }

            // Attempt send using the immediate method to avoid re-queuing
            boolean success = notificationService.sendPushImmediately(
                    notification.getUserId(),
                    notification.getTitle(),
                    notification.getBody(),
                    data);

            if (success) {
                notification.setStatus(NotificationQueue.NotificationStatus.SENT);
                notification.setUpdatedAt(LocalDateTime.now());
                queueRepository.save(notification);
                return true;
            } else {
                handleFailure(notification);
                queueRepository.save(notification);
                return false;
            }

        } catch (Exception e) {
            log.error("Error processing notification {}: {}", notification.getId(), e.getMessage());
            handleFailure(notification);
            queueRepository.save(notification);
            return false;
        }
    }

    private void handleFailure(NotificationQueue notification) {
        notification.setAttempts(notification.getAttempts() + 1);

        if (notification.getAttempts() >= maxRetries) {
            notification.setStatus(NotificationQueue.NotificationStatus.FAILED);
            log.warn("Notification {} exhausted retries, marking as FAILED", notification.getId());
        } else {
            // Exponential backoff: 1min, 5min, 25min
            long minutes = (long) Math.pow(5, notification.getAttempts());
            if (minutes < 1) {
                minutes = 1;
            }
            if (minutes > 60) {
                minutes = 60; // Cap at 1 hour
            }

            notification.setNextRetryAt(LocalDateTime.now().plusMinutes(minutes));
            log.debug("Notification {} will retry in {} minutes", notification.getId(), minutes);
        }
        notification.setUpdatedAt(LocalDateTime.now());
    }
}
