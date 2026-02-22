package com.carselling.oldcar.service.file;

import com.carselling.oldcar.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asynchronous view count service for high-throughput car detail reads.
 * Uses the existing {@code CarRepository.incrementViewCount} bulk UPDATE query
 * instead of load-modify-save, reducing write latency on the hot path.
 *
 * <p>
 * Includes in-memory deduplication to prevent the same car from being
 * incremented more than once within a short window (5 minutes).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ViewCountService {

    private final CarRepository carRepository;

    /**
     * Deduplication window in milliseconds (5 minutes).
     * Prevents the same carId from being incremented in rapid succession
     * (e.g. page refreshes, bots, duplicate requests).
     */
    private static final long DEDUP_WINDOW_MS = 5 * 60 * 1000L;

    /**
     * Key = carId, Value = timestamp of last increment.
     * ConcurrentHashMap for thread-safety under async execution.
     */
    private final Map<Long, Long> lastIncrementTimestamps = new ConcurrentHashMap<>();

    /**
     * Asynchronously increment the view count for a car using a single
     * bulk UPDATE query. Skips if the viewer is the car owner (owner views
     * should not inflate analytics). Also deduplicates within a 5-minute window.
     *
     * @param carId         the ID of the car whose view count to increment
     * @param currentUserId the ID of the user viewing the car (nullable for
     *                      anonymous)
     * @param ownerUserId   the ID of the car's owner
     */
    @Async
    @Transactional
    public void incrementAsync(Long carId, Long currentUserId, Long ownerUserId) {
        try {
            // Skip owner's own views — they should not inflate analytics
            if (currentUserId != null && currentUserId.equals(ownerUserId)) {
                log.debug("Skipping view count increment for owner {} viewing own car {}", currentUserId, carId);
                return;
            }

            long now = System.currentTimeMillis();
            Long lastIncrement = lastIncrementTimestamps.get(carId);

            if (lastIncrement != null && (now - lastIncrement) < DEDUP_WINDOW_MS) {
                log.debug("Skipping duplicate view count increment for car {}", carId);
                return;
            }

            carRepository.incrementViewCount(carId);
            lastIncrementTimestamps.put(carId, now);

            log.debug("Incremented view count for car {}", carId);

            // Periodic cleanup of stale dedup entries to prevent memory leak
            if (lastIncrementTimestamps.size() > 10_000) {
                cleanupStaleEntries(now);
            }
        } catch (Exception e) {
            // Fire-and-forget: log but never propagate to caller
            log.warn("Failed to increment view count for car {}: {}", carId, e.getMessage());
        }
    }

    /**
     * Remove dedup entries older than the dedup window.
     *
     * @param now current timestamp in millis
     */
    private void cleanupStaleEntries(long now) {
        int removed = 0;
        var iterator = lastIncrementTimestamps.entrySet().iterator();
        while (iterator.hasNext()) {
            if ((now - iterator.next().getValue()) > DEDUP_WINDOW_MS) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("Cleaned up {} stale view-count dedup entries", removed);
        }
    }
}
