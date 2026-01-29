package com.carselling.oldcar.service;

import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarInteractionEvent;
import com.carselling.oldcar.model.CarInteractionEvent.EventType;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarInteractionEventRepository;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for tracking car interaction events
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CarInteractionEventService {

    private final CarInteractionEventRepository eventRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;

    /**
     * Track a car interaction event (async for performance)
     */
    @Async
    public void trackEvent(Long carId, Long userId, EventType eventType, String sessionId, String deviceInfo,
            String ipAddress, String referrer, String metadata) {
        log.debug("Tracking {} event for car {} by user {}", eventType, carId, userId);

        try {
            Car car = carRepository.findById(carId).orElse(null);
            if (car == null) {
                log.warn("Car {} not found for event tracking", carId);
                return;
            }

            User user = null;
            if (userId != null) {
                user = userRepository.findById(userId).orElse(null);
            }

            // Exclude Dealer's own views from analytics
            if (user != null && car.getOwner().getId().equals(user.getId())) {
                log.debug("Skipping event tracking for owner viewing own car {} (user {})", carId, userId);
                return;
            }

            // Prevent duplicate view counts for same user on same day
            if (eventType == EventType.CAR_VIEW && user != null) {
                LocalDateTime todayStart = LocalDate.now().atStartOfDay();
                if (eventRepository.existsViewByCarIdAndUserIdToday(carId, userId, todayStart)) {
                    log.debug("Duplicate view event skipped for car {} by user {}", carId, userId);
                    return;
                }
            }

            // Ensure SAVE events are idempotent per user and car
            if (eventType == EventType.SAVE && user != null) {
                if (eventRepository.existsByCarIdAndUserIdAndEventType(carId, userId, eventType)) {
                    log.debug("Duplicate SAVE event skipped for car {} by user {}", carId, userId);
                    return;
                }
            }

            CarInteractionEvent event = CarInteractionEvent.builder()
                    .car(car)
                    .user(user)
                    .eventType(eventType)
                    .sessionId(sessionId)
                    .deviceInfo(deviceInfo)
                    .ipAddress(ipAddress)
                    .referrer(referrer)
                    .metadata(metadata)
                    .build();

            eventRepository.save(event);
            log.debug("{} event tracked for car {}", eventType, carId);

        } catch (Exception e) {
            log.error("Error tracking {} event for car {}: {}", eventType, carId, e.getMessage());
        }
    }

    /**
     * Shorthand for tracking common events
     */
    public void trackCarView(Long carId, Long userId, String sessionId) {
        trackEvent(carId, userId, EventType.CAR_VIEW, sessionId, null, null, null, null);
    }

    public void trackContactClick(Long carId, Long userId) {
        trackEvent(carId, userId, EventType.CONTACT_CLICK, null, null, null, null, null);
    }

    public void trackSave(Long carId, Long userId) {
        trackEvent(carId, userId, EventType.SAVE, null, null, null, null, null);
    }

    public void trackShare(Long carId, Long userId, String platform) {
        trackEvent(carId, userId, EventType.SHARE, null, null, null, null, "{\"platform\":\"" + platform + "\"}");
    }

    public void trackChatOpen(Long carId, Long userId) {
        trackEvent(carId, userId, EventType.CHAT_OPEN, null, null, null, null, null);
    }

    /**
     * Get event statistics for a car
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getCarEventStats(Long carId) {
        Map<String, Long> stats = new HashMap<>();

        List<Object[]> results = eventRepository.getEventCountsByCarId(carId);
        for (Object[] row : results) {
            EventType eventType = (EventType) row[0];
            Long count = (Long) row[1];
            stats.put(eventType.name(), count);
        }

        return stats;
    }

    /**
     * Get view count for a car
     */
    @Transactional(readOnly = true)
    public long getViewCount(Long carId) {
        return eventRepository.countViewsByCarId(carId);
    }

    /**
     * Get daily event trend for a car (last N days)
     */
    @Transactional(readOnly = true)
    public List<Object[]> getDailyTrend(Long carId, int days) {
        LocalDateTime startDate = LocalDate.now().minusDays(days).atStartOfDay();
        return eventRepository.getDailyEventCountsByCarId(carId, startDate);
    }

    /**
     * Get aggregated stats for all cars owned by a dealer
     */
    @Transactional(readOnly = true)
    public Map<Long, Map<String, Long>> getDealerCarStats(Long dealerId) {
        Map<Long, Map<String, Long>> carStats = new HashMap<>();

        List<Object[]> results = eventRepository.getEventCountsByDealerId(dealerId);
        for (Object[] row : results) {
            Long carId = (Long) row[0];
            EventType eventType = (EventType) row[1];
            Long count = (Long) row[2];

            carStats.computeIfAbsent(carId, k -> new HashMap<>()).put(eventType.name(), count);
        }

        return carStats;
    }
}
