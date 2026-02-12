package com.carselling.oldcar.service.analytics;

import com.carselling.oldcar.dto.analytics.AnalyticsBatchDto;
import com.carselling.oldcar.dto.analytics.AnalyticsEventDto;
import com.carselling.oldcar.dto.analytics.SessionStartDto;
import com.carselling.oldcar.model.UserAnalyticsEvent;
import com.carselling.oldcar.model.UserAnalyticsEvent.EventType;
import com.carselling.oldcar.model.UserAnalyticsEvent.TargetType;
import com.carselling.oldcar.model.UserSession;
import com.carselling.oldcar.repository.CarInteractionEventRepository;
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
    private final CarInteractionEventRepository carEventRepository;
    private final UserSessionRepository sessionRepository;
    private final CarRepository carRepository;
    private final com.carselling.oldcar.repository.ChatRoomRepository chatRoomRepository;
    private final com.carselling.oldcar.repository.ChatParticipantRepository chatParticipantRepository;
    private final com.carselling.oldcar.service.car.CarService carService;
    private final com.carselling.oldcar.repository.UserRepository userRepository;

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
    @Async("analyticsTaskExecutor")
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
    /**
     * Check if user owns the car
     */
    @Transactional(readOnly = true)
    public boolean checkCarOwnership(Long userId, String carId) {
        if (userId == null || carId == null)
            return false;

        try {
            return carRepository.existsByIdAndOwnerId(Long.parseLong(carId), userId);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // =============== INSIGHTS ===============

    /**
     * Get car-level insights (Legacy / Quick Check)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCarInsights(String carId) {
        // Use generic event repo for this legacy method or simple check
        Map<String, Object> insights = new HashMap<>();
        try {
            Long cId = Long.parseLong(carId);

            // Use CarInteractionEventRepository for accuracy
            List<Object[]> eventCounts = carEventRepository.getEventCountsByCarId(cId);
            Map<String, Long> events = new HashMap<>();
            for (Object[] row : eventCounts) {
                events.put(((com.carselling.oldcar.model.CarInteractionEvent.EventType) row[0]).name(), (Long) row[1]);
            }
            insights.put("eventCounts", events);

            insights.put("totalViews", carEventRepository.countViewsByCarId(cId));
            insights.put("uniqueViewers", carEventRepository.countUniqueViewersByCarId(cId));

            // Trend (last 30 days)
            LocalDateTime startDate = LocalDateTime.now().minusDays(30);
            List<Object[]> dailyViews = carEventRepository.getDailyViewCountsByCarId(cId, startDate);
            insights.put("dailyViews", dailyViews);

        } catch (NumberFormatException e) {
            // fallback
        }
        return insights;
    }

    /**
     * Get Dealer
     * 
     * performance insights (List of cars)
     */

    @Transactional(readOnly = true)
    public Map<String, Object> getDealerInsights(List<String> carIds) {
        Map<String, Object> insights = new HashMap<>();

        if (carIds == null || carIds.isEmpty()) {
            insights.put("totalEngagement", Collections.emptyMap());
            return insights;
        }

        List<Object[]> engagement = eventRepository.getDealerTotalEngagement(carIds);
        Map<String, Long> events = new HashMap<>();
        for (Object[] row : engagement) {
            events.put(((EventType) row[0]).name(), (Long) row[1]);
        }
        return insights;
    }

    /**
     * Get full analytics for a single car (Event-Sourced).
     * Calculates KPIs like conversion rate, engagement score, and contact rate.
     */
    @Transactional(readOnly = true)
    public com.carselling.oldcar.dto.car.CarAnalyticsResponse getCarAnalytics(Long carId) {
        // 1. Get raw event counts from CarInteractionEvent
        List<Object[]> eventCounts = carEventRepository.getEventCountsByCarId(carId);
        long views = 0;
        long shares = 0;
        long saves = 0;
        long chatInquiries = 0;
        long callInquiries = 0;
        long whatsappInquiries = 0;
        long contactClicks = 0;

        for (Object[] row : eventCounts) {
            com.carselling.oldcar.model.CarInteractionEvent.EventType type = (com.carselling.oldcar.model.CarInteractionEvent.EventType) row[0];
            Long count = (Long) row[1];
            switch (type) {
                case CAR_VIEW -> views = count;
                case SHARE -> shares = count;
                case SAVE -> saves = count;
                case CHAT_OPEN -> chatInquiries = count;
                case CALL_CLICK -> callInquiries = count;
                case WHATSAPP_CLICK -> whatsappInquiries = count;
                case CONTACT_CLICK -> contactClicks = count;
                case TEST_DRIVE_REQUEST -> contactClicks += count;
                case COMPARE_ADD, IMAGE_VIEW, UNSAVE -> {
                } // Ignore these for now
            }
        }

        // 2. Unique viewers
        long uniqueViewers = carEventRepository.countUniqueViewersByCarId(carId);

        // 3. KPI Calculations
        long totalContacts = chatInquiries + callInquiries + whatsappInquiries + contactClicks;

        double conversionRate = 0.0;
        if (views > 0) {
            conversionRate = ((double) totalContacts / views) * 100.0;
        }

        double contactRate = 0.0;
        if (uniqueViewers > 0) {
            contactRate = ((double) totalContacts / uniqueViewers) * 100.0;
        }

        // Engagement Score (0-100)
        long weightedScore = (views * 1) + (saves * 10) + (shares * 20) + (totalContacts * 50);
        int engagementScore = (int) Math.min(100, (weightedScore / 10));

        // 4. Daily Views Trend (Last 30 days)
        LocalDateTime thirtyDaysAgo = java.time.LocalDate.now().minusDays(30).atStartOfDay();
        List<Object[]> dailyViewData = carEventRepository.getDailyViewCountsByCarId(carId, thirtyDaysAgo);
        List<com.carselling.oldcar.dto.car.CarAnalyticsResponse.DailyView> viewsTimeline = new ArrayList<>();

        long dailyViews = 0; // Today
        long weeklyViews = 0; // Last 7 days
        long monthlyViews = 0; // Last 30 days
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate sevenDaysAgoDate = today.minusDays(7);

        for (Object[] row : dailyViewData) {
            java.time.LocalDate date = null;
            if (row[0] instanceof java.sql.Date) {
                date = ((java.sql.Date) row[0]).toLocalDate();
            } else if (row[0] instanceof java.util.Date) {
                date = ((java.util.Date) row[0]).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            } else {
                date = java.time.LocalDate.parse(row[0].toString());
            }

            Long count = (Long) row[1];

            viewsTimeline.add(com.carselling.oldcar.dto.car.CarAnalyticsResponse.DailyView.builder()
                    .date(date.toString())
                    .views(count)
                    .build());

            monthlyViews += count;
            if (!date.isBefore(sevenDaysAgoDate)) {
                weeklyViews += count;
            }
            if (date.equals(today)) {
                dailyViews += count;
            }
        }

        return com.carselling.oldcar.dto.car.CarAnalyticsResponse.builder()
                .vehicleId(carId.toString())
                .views(views)
                .uniqueViewers(uniqueViewers)
                .inquiries(totalContacts)
                .shares(shares)
                .saves(saves)
                .conversionRate(Math.round(conversionRate * 10.0) / 10.0)
                .contactRate(Math.round(contactRate * 10.0) / 10.0)
                .engagementScore(engagementScore)
                .viewsBreakdown(com.carselling.oldcar.dto.car.CarAnalyticsResponse.ViewsBreakdown.builder()
                        .dailyViews(dailyViews)
                        .weeklyViews(weeklyViews)
                        .monthlyViews(monthlyViews)
                        .viewsTimeline(viewsTimeline)
                        .build())
                .inquiriesBreakdown(com.carselling.oldcar.dto.car.CarAnalyticsResponse.InquiriesBreakdown.builder()
                        .chatInquiries(chatInquiries)
                        .callInquiries(callInquiries)
                        .whatsappInquiries(whatsappInquiries)
                        .totalContacts(totalContacts)
                        .commonQuestions(List.of())
                        .build())
                .build();
    }

    /**
     * Get Dealer Dashboard Statistics
     */
    @Transactional(readOnly = true)
    public com.carselling.oldcar.dto.car.DealerDashboardResponse getDealerDashboardStats(Long dealerId) {
        log.debug("Getting dealer dashboard statistics for user: {}", dealerId);

        // Use CarInteractionEventRepository logic (Aggregate all cars)
        List<Long> carIds = carRepository.findCarIdsByOwnerId(dealerId);

        long totalViews = 0;
        long totalShares = 0;
        long totalSaves = 0;
        long totalInquiriesEvents = 0;

        if (!carIds.isEmpty()) {
            List<Object[]> dealerEvents = carEventRepository.getDealerTotalEngagement(carIds);
            for (Object[] row : dealerEvents) {
                com.carselling.oldcar.model.CarInteractionEvent.EventType type = (com.carselling.oldcar.model.CarInteractionEvent.EventType) row[0];
                Long count = (Long) row[1];

                if (type == com.carselling.oldcar.model.CarInteractionEvent.EventType.CAR_VIEW) {
                    totalViews += count;
                } else if (type == com.carselling.oldcar.model.CarInteractionEvent.EventType.SHARE) {
                    totalShares += count;
                } else if (type == com.carselling.oldcar.model.CarInteractionEvent.EventType.SAVE) {
                    totalSaves += count;
                } else if (type == com.carselling.oldcar.model.CarInteractionEvent.EventType.CONTACT_CLICK
                        || type == com.carselling.oldcar.model.CarInteractionEvent.EventType.CHAT_OPEN
                        || type == com.carselling.oldcar.model.CarInteractionEvent.EventType.WHATSAPP_CLICK
                        || type == com.carselling.oldcar.model.CarInteractionEvent.EventType.CALL_CLICK) {
                    totalInquiriesEvents += count;
                }
            }
        }

        // 2. Counts from other sources
        long contactRequests = chatRoomRepository.countCarInquiryChatsForSeller(dealerId);
        long totalUniqueVisitors = chatParticipantRepository.countUniqueInquiryUsersForSeller(dealerId);
        long totalCarsAdded = carRepository.countByOwnerId(dealerId);
        long activeCars = carRepository.countActiveCarsByOwnerId(dealerId);

        // 3. Calculate KPIs
        // Total inquiries = chat requests (actual chats) + click events (intent)
        long totalLeads = contactRequests + totalInquiriesEvents;

        double conversionRate = 0.0;
        if (totalViews > 0) {
            conversionRate = ((double) totalLeads / totalViews) * 100.0;
        }

        return com.carselling.oldcar.dto.car.DealerDashboardResponse.builder()
                .totalViews(totalViews)
                .totalUniqueVisitors(totalUniqueVisitors)
                .totalCarsAdded(totalCarsAdded)
                .activeCars(activeCars)
                .contactRequestsReceived(contactRequests)
                .totalShares(totalShares)
                .totalSaves(totalSaves)
                .conversionRate(Math.round(conversionRate * 10.0) / 10.0)
                .build();
    }

    /**
     * Get Detailed Dealer Analytics (Charts & Tables)
     */
    @Transactional(readOnly = true)
    public com.carselling.oldcar.dto.car.DealerAnalyticsResponse getDealerAnalytics(Long dealerId) {
        // 1. Get all car IDs for this dealer
        List<Long> carIdLongs = carRepository.findCarIdsByOwnerId(dealerId);

        if (carIdLongs.isEmpty()) {
            return com.carselling.oldcar.dto.car.DealerAnalyticsResponse.builder()
                    .totalVehicles(0)
                    .totalViews(0)
                    .totalInquiries(0)
                    .totalShares(0)
                    .totalSaves(0)
                    .avgDaysOnMarket(0)
                    .conversionRate(0.0)
                    .engagementScore(0)
                    .monthlyStats(Collections.emptyList())
                    .locationStats(Collections.emptyList())
                    .topPerformers(Collections.emptyList())
                    .build();
        }

        // 2. Aggregate Totals
        List<Object[]> totalEngagement = carEventRepository.getDealerTotalEngagement(carIdLongs);

        long totalViews = 0;
        long totalInquiries = 0;
        long totalShares = 0;
        long totalSaves = 0;

        for (Object[] row : totalEngagement) {
            com.carselling.oldcar.model.CarInteractionEvent.EventType type = (com.carselling.oldcar.model.CarInteractionEvent.EventType) row[0];
            Long count = (Long) row[1];
            if (type == com.carselling.oldcar.model.CarInteractionEvent.EventType.CAR_VIEW)
                totalViews += count;
            else if (type == com.carselling.oldcar.model.CarInteractionEvent.EventType.CONTACT_CLICK
                    || type == com.carselling.oldcar.model.CarInteractionEvent.EventType.CALL_CLICK
                    || type == com.carselling.oldcar.model.CarInteractionEvent.EventType.CHAT_OPEN
                    || type == com.carselling.oldcar.model.CarInteractionEvent.EventType.WHATSAPP_CLICK)
                totalInquiries += count;
            else if (type == com.carselling.oldcar.model.CarInteractionEvent.EventType.SHARE)
                totalShares += count;
            else if (type == com.carselling.oldcar.model.CarInteractionEvent.EventType.SAVE)
                totalSaves += count;
        }

        // 3. Calculate KPIs
        double conversionRate = 0.0;
        if (totalViews > 0) {
            conversionRate = ((double) totalInquiries / totalViews) * 100.0;
        }

        // Engagement Score Calculation (Average across portfolio)
        long totalWeightedScore = (totalViews * 1) + (totalSaves * 10) + (totalShares * 20) + (totalInquiries * 50);
        int engagementScore = 0;
        if (!carIdLongs.isEmpty()) {
            long avgWeightedScore = totalWeightedScore / carIdLongs.size();
            engagementScore = (int) Math.min(100, avgWeightedScore / 5);
        }

        // 4. Calculate Avg Days on Market
        Double avgDaysVal = carRepository.getAverageCarAgeInDaysByOwnerId(dealerId);
        double avgDays = avgDaysVal != null ? avgDaysVal : 0.0;

        // 5. Monthly Stats (Last 6 Months)
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        List<Object[]> monthlyViews = carEventRepository.getDealerMonthlyViews(carIdLongs, sixMonthsAgo);
        List<Object[]> monthlyInquiries = carEventRepository.getDealerMonthlyInquiries(carIdLongs, sixMonthsAgo);

        Map<String, com.carselling.oldcar.dto.car.DealerAnalyticsResponse.MonthlyStat> statsMap = new LinkedHashMap<>();

        // Populate views
        for (Object[] row : monthlyViews) {
            String month = (String) row[0];
            statsMap.putIfAbsent(month,
                    com.carselling.oldcar.dto.car.DealerAnalyticsResponse.MonthlyStat.builder().month(month).build());
            statsMap.get(month).setViews((Long) row[1]);
        }

        // Populate inquiries
        for (Object[] row : monthlyInquiries) {
            String month = (String) row[0];
            statsMap.putIfAbsent(month,
                    com.carselling.oldcar.dto.car.DealerAnalyticsResponse.MonthlyStat.builder().month(month).build());
            statsMap.get(month).setInquiries((Long) row[1]);
        }

        // 6. Location Stats
        List<Object[]> locationData = carEventRepository.getDealerLocationStats(carIdLongs);
        List<com.carselling.oldcar.dto.car.DealerAnalyticsResponse.LocationStat> locationStats = locationData.stream()
                .limit(5)
                .map(row -> com.carselling.oldcar.dto.car.DealerAnalyticsResponse.LocationStat.builder()
                        .location((String) row[0])
                        .count((Long) row[1])
                        .build())
                .toList();

        // 7. Top Performers (Top 5 viewed cars)
        List<Long> topCarIds = carEventRepository.findTopPerformedCarIds(carIdLongs,
                org.springframework.data.domain.PageRequest.of(0, 5));

        List<com.carselling.oldcar.dto.car.CarResponse> topPerformers = Collections.emptyList();

        if (!topCarIds.isEmpty()) {
            List<String> topCarIdStrings = topCarIds.stream().map(Object::toString).toList();
            topPerformers = carService.getVehiclesByIds(topCarIdStrings);
        }

        return com.carselling.oldcar.dto.car.DealerAnalyticsResponse.builder()
                .totalVehicles(carIdLongs.size())
                .totalViews(totalViews)
                .totalInquiries(totalInquiries)
                .totalShares(totalShares)
                .totalSaves(totalSaves)
                .avgDaysOnMarket(avgDays)
                .conversionRate(Math.round(conversionRate * 10.0) / 10.0)
                .engagementScore(engagementScore)
                .monthlyStats(new ArrayList<>(statsMap.values()))
                .locationStats(locationStats)
                .topPerformers(topPerformers)
                .build();
    }

    /**
     * Get list of users who viewed dealer's cars (Leads)
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.carselling.oldcar.dto.analytics.CarViewerDto> getCarViewers(
            Long dealerId, org.springframework.data.domain.Pageable pageable) {

        // 1. Get all car IDs for this dealer
        List<Long> carIdLongs = carRepository.findCarIdsByOwnerId(dealerId);
        if (carIdLongs.isEmpty()) {
            return org.springframework.data.domain.Page.empty(pageable);
        }

        // 2. Aggregate views by User+Car
        org.springframework.data.domain.Slice<Object[]> slice = carEventRepository.getUserViewCountsForCars(carIdLongs,
                pageable);
        List<Object[]> rows = slice.getContent();

        if (rows.isEmpty()) {
            return org.springframework.data.domain.Page.empty(pageable);
        }

        // 3. Extract IDs for bulk fetching
        Set<Long> userIds = new HashSet<>();
        Set<Long> involvedCarIds = new HashSet<>();

        for (Object[] row : rows) {
            userIds.add((Long) row[0]);
            involvedCarIds.add((Long) row[1]);
        }

        // 4. Fetch Entities
        Map<Long, com.carselling.oldcar.model.User> userMap = userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(com.carselling.oldcar.model.User::getId, u -> u));

        Map<Long, com.carselling.oldcar.model.Car> carMap = carRepository.findAllById(involvedCarIds).stream()
                .collect(java.util.stream.Collectors.toMap(com.carselling.oldcar.model.Car::getId, c -> c));

        // 5. Assemble DTOs
        List<com.carselling.oldcar.dto.analytics.CarViewerDto> dtos = new ArrayList<>();

        for (Object[] row : rows) {
            Long userId = (Long) row[0];
            Long carId = (Long) row[1];
            Long count = (Long) row[2];

            com.carselling.oldcar.model.User user = userMap.get(userId);
            com.carselling.oldcar.model.Car car = carMap.get(carId);

            if (user != null && car != null) {
                dtos.add(com.carselling.oldcar.dto.analytics.CarViewerDto.builder()
                        .userId(userId)
                        .userName(user.getDisplayName())
                        .userEmail(user.getEmail())
                        .userProfileImage(user.getProfileImageUrl())
                        .carId(carId.toString())
                        .carMake(car.getMake())
                        .carModel(car.getModel())
                        .carYear(car.getYear())
                        .carImage(car.getImages().isEmpty() ? null : car.getImages().get(0))
                        .carPrice(car.getPrice() != null ? car.getPrice().longValue() : 0L)
                        .viewCount(count)
                        .lastViewedAt(count + " views")
                        .build());
            }
        }

        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, dtos.size());
    }

    // =============== CLEANUP ===============

    /**
     * Reset rate limit counters every minute
     */
    @Scheduled(fixedRate = 60000)
    public void resetRateLimitCounters() {
        sessionEventCounts.clear();
        // log.debug("Reset rate limit counters");
    }

    /**
     * Expire stale sessions every 5 minutes
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void expireStaleSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        // Batch processing to avoid large table locks
        int totalExpired = 0;
        // Process up to 10 batches (10,000 records) per run to avoid infinite loops if
        // backlog is huge
        for (int i = 0; i < 10; i++) {
            List<UserSession> staleSessions = sessionRepository.findTop1000ByIsActiveTrueAndStartedAtBefore(cutoff);
            if (staleSessions.isEmpty()) {
                break;
            }

            List<String> ids = staleSessions.stream().map(UserSession::getSessionId).toList();
            int count = sessionRepository.expireSessionsByIds(ids);
            totalExpired += count;
            log.debug("Expired batch {} of {} sessions", i + 1, count);
        }

        if (totalExpired > 0) {
            log.info("Expired total {} stale sessions", totalExpired);
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
