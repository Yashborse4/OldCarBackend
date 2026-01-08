package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.analytics.AnalyticsBatchDto;
import com.carselling.oldcar.dto.analytics.AnalyticsEventDto;
import com.carselling.oldcar.dto.analytics.SessionStartDto;
import com.carselling.oldcar.model.UserAnalyticsEvent;
import com.carselling.oldcar.model.UserAnalyticsEvent.EventType;
import com.carselling.oldcar.model.UserAnalyticsEvent.TargetType;
import com.carselling.oldcar.model.UserSession;
import com.carselling.oldcar.repository.UserAnalyticsEventRepository;
import com.carselling.oldcar.repository.UserSessionRepository;
import com.carselling.oldcar.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for processing and storing user analytics events.
 * Handles batching, throttling, and async processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAnalyticsService {

    private final UserAnalyticsEventRepository eventRepository;
    private final UserSessionRepository sessionRepository;
    private final CarRepository carRepository;

    // Rate limiting: track events per session per minute
    private final ConcurrentHashMap<String, AtomicInteger> sessionEventCounts = new ConcurrentHashMap<>();
    private static final int MAX_EVENTS_PER_MINUTE = 100;

    // Retention policy: 90 days for raw events
    private static final int RETENTION_DAYS = 90;

    // =============== SESSION MANAGEMENT ===============

    /**
     * Start a new session
     */
    @Transactional
    public void startSession(Long userId, SessionStartDto dto) {
        log.debug("Starting session {} for user {}", dto.getSessionId(), userId);

        // End any existing active sessions for this user
        if (userId != null) {
            List<UserSession> activeSessions = sessionRepository.findByUserIdAndIsActiveTrue(userId);
            for (UserSession session : activeSessions) {
                session.endSession();
                sessionRepository.save(session);
            }
        }

        UserSession session = UserSession.builder()
                .sessionId(dto.getSessionId())
                .userId(userId)
                .startedAt(LocalDateTime.now())
                .deviceType(dto.getDeviceType())
                .deviceModel(dto.getDeviceModel())
                .appVersion(dto.getAppVersion())
                .osVersion(dto.getOsVersion())
                .city(dto.getCity())
                .entryScreen(dto.getEntryScreen())
                .isActive(true)
                .eventCount(0)
                .screenCount(0)
                .carsViewed(0)
                .screensVisited(new ArrayList<>())
                .build();

        sessionRepository.save(session);
        log.info("Session {} started for user {}", dto.getSessionId(), userId);
    }

    /**
     * End a session
     */
    @Transactional
    public void endSession(String sessionId, String exitScreen) {
        log.debug("Ending session {}", sessionId);

        sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setExitScreen(exitScreen);
            session.endSession();
            sessionRepository.save(session);
            log.info("Session {} ended. Duration: {}s, Events: {}",
                    sessionId, session.getDurationSeconds(), session.getEventCount());
        });

        // Clear rate limit counter
        sessionEventCounts.remove(sessionId);
    }

    // =============== EVENT INGESTION ===============

    /**
     * Ingest a batch of events (async for performance)
     */
    @Async
    @Transactional
    public void ingestEvents(Long userId, AnalyticsBatchDto batch) {
        String sessionId = batch.getSessionId();

        // Rate limiting check
        AtomicInteger counter = sessionEventCounts.computeIfAbsent(sessionId, k -> new AtomicInteger(0));
        if (counter.get() >= MAX_EVENTS_PER_MINUTE) {
            log.warn("Rate limit exceeded for session {}, dropping {} events",
                    sessionId, batch.getEvents().size());
            return;
        }

        log.debug("Ingesting {} events for session {}", batch.getEvents().size(), sessionId);

        List<UserAnalyticsEvent> events = new ArrayList<>();
        Set<String> carsViewed = new HashSet<>();
        Set<String> screensViewed = new HashSet<>();

        for (AnalyticsEventDto dto : batch.getEvents()) {
            // Check rate limit for each event
            if (counter.incrementAndGet() > MAX_EVENTS_PER_MINUTE) {
                log.warn("Rate limit reached for session {}", sessionId);
                break;
            }

            UserAnalyticsEvent event = convertToEntity(userId, batch, dto);
            if (event != null) {
                events.add(event);

                // Track unique cars and screens
                if (event.getTargetType() == TargetType.CAR && event.getEventType() == EventType.CAR_VIEW) {
                    carsViewed.add(event.getTargetId());
                }
                if (event.getEventType() == EventType.SCREEN_VIEW && event.getScreenName() != null) {
                    screensViewed.add(event.getScreenName());
                }
            }
        }

        // Batch save events
        if (!events.isEmpty()) {
            eventRepository.saveAll(events);
            log.debug("Saved {} analytics events for session {}", events.size(), sessionId);
        }

        // Update session stats
        updateSessionStats(sessionId, events.size(), carsViewed.size(), screensViewed);
    }

    /**
     * Convert DTO to entity
     */
    private UserAnalyticsEvent convertToEntity(Long userId, AnalyticsBatchDto batch, AnalyticsEventDto dto) {
        try {
            EventType eventType = EventType.valueOf(dto.getEventType());
            TargetType targetType = dto.getTargetType() != null ? TargetType.valueOf(dto.getTargetType()) : null;

            return UserAnalyticsEvent.builder()
                    .userId(userId)
                    .sessionId(batch.getSessionId())
                    .eventType(eventType)
                    .targetType(targetType)
                    .targetId(dto.getTargetId())
                    .metadata(sanitizeMetadata(dto.getMetadata()))
                    .screenName(dto.getScreenName())
                    .previousScreen(dto.getPreviousScreen())
                    .deviceType(batch.getDeviceType())
                    .appVersion(batch.getAppVersion())
                    .osVersion(batch.getOsVersion())
                    .city(batch.getCity())
                    .sessionDurationSeconds(dto.getSessionDurationSeconds())
                    .actionDurationSeconds(dto.getActionDurationSeconds())
                    .clientTimestamp(dto.getClientTimestamp())
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid event type or target type: {} / {}",
                    dto.getEventType(), dto.getTargetType());
            return null;
        }
    }

    /**
     * Sanitize metadata to remove any PII
     */
    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null)
            return null;

        Map<String, Object> sanitized = new HashMap<>(metadata);
        // Remove any potential PII fields
        sanitized.remove("email");
        sanitized.remove("phone");
        sanitized.remove("name");
        sanitized.remove("password");
        sanitized.remove("token");

        return sanitized;
    }

    /**
     * Update session statistics
     */
    @Transactional
    protected void updateSessionStats(String sessionId, int eventCount, int newCarsViewed, Set<String> newScreens) {
        sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setEventCount(session.getEventCount() + eventCount);
            session.setCarsViewed(session.getCarsViewed() + newCarsViewed);
            session.setScreenCount(session.getScreenCount() + newScreens.size());

            // Append new screens to visited list
            List<String> screens = session.getScreensVisited();
            if (screens == null)
                screens = new ArrayList<>();
            for (String screen : newScreens) {
                if (!screens.contains(screen)) {
                    screens.add(screen);
                }
            }
            session.setScreensVisited(screens);

            sessionRepository.save(session);
        });
    }

    /**
     * Check if user owns the car
     */
    @Transactional(readOnly = true)
    public boolean checkCarOwnership(Long userId, String carId) {
        if (userId == null || carId == null)
            return false;

        return carRepository.findById(Long.parseLong(carId))
                .map(car -> car.getOwner().getId().equals(userId))
                .orElse(false);
    }

    // =============== INSIGHTS ===============

    /**
     * Get car-level insights
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCarInsights(String carId) {
        Map<String, Object> insights = new HashMap<>();

        // Event counts by type
        List<Object[]> eventCounts = eventRepository.getCarEventCounts(carId);
        Map<String, Long> events = new HashMap<>();
        for (Object[] row : eventCounts) {
            events.put(((EventType) row[0]).name(), (Long) row[1]);
        }
        insights.put("eventCounts", events);

        // Basic metrics
        insights.put("totalViews", eventRepository.countCarViews(carId));
        insights.put("uniqueViewers", eventRepository.countUniqueCarViewers(carId));
        insights.put("avgViewDuration", eventRepository.getAverageCarViewDuration(carId));

        // Trend (last 30 days)
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        List<Object[]> dailyViews = eventRepository.getDailyCarViews(carId, startDate);
        insights.put("dailyViews", dailyViews);

        return insights;
    }

    /**
     * Get dealer performance insights
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDealerInsights(List<String> carIds) {
        Map<String, Object> insights = new HashMap<>();

        List<Object[]> engagement = eventRepository.getDealerTotalEngagement(carIds);
        Map<String, Long> events = new HashMap<>();
        for (Object[] row : engagement) {
            events.put(((EventType) row[0]).name(), (Long) row[1]);
        }
        insights.put("totalEngagement", events);

        return insights;
    }

    // =============== CLEANUP ===============

    /**
     * Reset rate limit counters every minute
     */
    @Scheduled(fixedRate = 60000)
    public void resetRateLimitCounters() {
        sessionEventCounts.clear();
        log.debug("Reset rate limit counters");
    }

    /**
     * Expire stale sessions every 5 minutes
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void expireStaleSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        int expired = sessionRepository.expireStaleSessions(cutoff);
        if (expired > 0) {
            log.info("Expired {} stale sessions", expired);
        }
    }

    /**
     * Delete old events daily (retention policy)
     */
    @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
    @Transactional
    public void cleanupOldEvents() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int deleted = eventRepository.deleteOldEvents(cutoffDate);
        log.info("Deleted {} events older than {} days", deleted, RETENTION_DAYS);

        int deletedSessions = sessionRepository.deleteOldSessions(cutoffDate);
        log.info("Deleted {} sessions older than {} days", deletedSessions, RETENTION_DAYS);
    }
}
