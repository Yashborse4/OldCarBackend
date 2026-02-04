package com.carselling.oldcar.controller.analytics;

import com.carselling.oldcar.dto.analytics.AnalyticsBatchDto;
import com.carselling.oldcar.dto.analytics.SessionStartDto;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.mobile.MobileAnalyticsRequest;
import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.analytics.UserAnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for analytics event ingestion.
 * Designed for high-volume, low-latency operations.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "Analytics", description = "User analytics and session tracking")
public class UserAnalyticsController {

    private final UserAnalyticsService analyticsService;

    /**
     * Start a new session
     * POST /api/analytics/session/start
     */
    @PostMapping("/session/start")
    @io.swagger.v3.oas.annotations.Operation(summary = "Start session", description = "Initialize a new analytics session")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session started"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<ApiResponse<Object>> startSession(
            @Valid @RequestBody SessionStartDto dto,
            Authentication authentication) {

        Long userId = getUserId(authentication);
        log.debug("Starting session {} for user {}", dto.getSessionId(), userId);

        analyticsService.startSession(userId, dto);

        return ResponseEntity.ok(ApiResponse.success(
                "Session started",
                "Session " + dto.getSessionId() + " created"));
    }

    /**
     * End a session
     * POST /api/analytics/session/end
     */
    @PostMapping("/session/end")
    @io.swagger.v3.oas.annotations.Operation(summary = "End session", description = "Terminate an analytics session")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session ended"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found")
    })
    public ResponseEntity<ApiResponse<Object>> endSession(
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "exitScreen", required = false) String exitScreen,
            Authentication authentication) {

        log.debug("Ending session {}", sessionId);
        analyticsService.endSession(sessionId, exitScreen);

        return ResponseEntity.ok(ApiResponse.success(
                "Session ended",
                "Session " + sessionId + " closed"));
    }

    /**
     * Ingest a batch of events
     * POST /api/analytics/events
     * 
     * This endpoint is optimized for high-volume ingestion:
     * - Accepts batches of up to 100 events
     * - Processes asynchronously
     * - Rate limited to 100 events/minute per session
     */
    @PostMapping("/events")
    @io.swagger.v3.oas.annotations.Operation(summary = "Ingest events", description = "Batch ingest analytics events")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Events accepted for processing"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid event data"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<ApiResponse<Object>> ingestEvents(
            @Valid @RequestBody AnalyticsBatchDto batch,
            Authentication authentication) {

        Long userId = getUserId(authentication);
        int eventCount = batch.getEvents().size();

        log.debug("Ingesting {} events for session {}", eventCount, batch.getSessionId());

        // Process asynchronously
        analyticsService.ingestEvents(userId, batch);

        return ResponseEntity.accepted().body(ApiResponse.success(
                "Events accepted",
                eventCount + " events queued for processing"));
    }

    /**
     * Get car insights (for car owners/dealers)
     * GET /api/analytics/car/{carId}/insights
     */
    @GetMapping("/car/{carId}/insights")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCarInsights(
            @PathVariable String carId,
            Authentication authentication) {

        log.info("Getting analytics insights for car {}", carId);

        Long userId = getUserId(authentication);
        if (userId == null || !analyticsService.checkCarOwnership(userId, carId)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("Access denied", "Only the car owner can view insights"));
        }

        Map<String, Object> insights = analyticsService.getCarInsights(carId);

        return ResponseEntity.ok(ApiResponse.success(
                "Car insights retrieved",
                "Analytics for car " + carId,
                insights));
    }

    /**
     * Get list of users who viewed this dealer's cars (Leads)
     * GET /api/analytics/dealer/viewers
     */
    @GetMapping("/dealer/viewers")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get interested users", description = "Get list of users who viewed dealer's cars, sorted by view count")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<com.carselling.oldcar.dto.analytics.CarViewerDto>>> getDealerViewers(
            @org.springdoc.core.annotations.ParameterObject org.springframework.data.domain.Pageable pageable,
            Authentication authentication) {

        Long dealerId = getUserId(authentication);
        if (dealerId == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Authentication required", "User must be logged in"));
        }

        log.info("Getting interested users for dealer {}", dealerId);

        org.springframework.data.domain.Page<com.carselling.oldcar.dto.analytics.CarViewerDto> viewers = analyticsService
                .getCarViewers(dealerId, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Interested users retrieved",
                "Found " + viewers.getTotalElements() + " interested users",
                viewers));
    }

    /**
     * Report mobile app usage analytics
     * POST /api/analytics/mobile-events
     */
    @PostMapping("/mobile-events")
    public ResponseEntity<ApiResponse<String>> reportMobileAnalytics(
            @Valid @RequestBody MobileAnalyticsRequest request,
            Authentication authentication) {

        Long userId = getUserId(authentication);
        log.info("Received mobile analytics data from user: {}", userId);

        try {
            // Map MobileAnalyticsRequest to AnalyticsBatchDto
            AnalyticsBatchDto batch = new AnalyticsBatchDto();
            // Use deviceId as sessionId if not provided in request (mobile app specific
            // constraint)
            batch.setSessionId(request.getDeviceId() != null ? request.getDeviceId() : "unknown-mobile-session");
            batch.setDeviceType("mobile");
            batch.setDeviceModel(request.getPlatform()); // Mapping platform to model/os roughly
            batch.setAppVersion(request.getAppVersion());

            List<com.carselling.oldcar.dto.analytics.AnalyticsEventDto> events = request.getEvents().stream()
                    .map(this::mapMobileEvent)
                    .filter(java.util.Objects::nonNull)
                    .toList();

            batch.setEvents(events);

            if (!events.isEmpty()) {
                analyticsService.ingestEvents(userId, batch);
                return ResponseEntity
                        .ok(ApiResponse.success("Mobile analytics data queued using " + events.size() + " events"));
            } else {
                return ResponseEntity.ok(ApiResponse.success("No valid events to process"));
            }

        } catch (Exception e) {
            log.error("Error processing mobile analytics", e);
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid analytics data", e.getMessage()));
        }
    }

    private com.carselling.oldcar.dto.analytics.AnalyticsEventDto mapMobileEvent(Map<String, Object> eventData) {
        try {
            com.carselling.oldcar.dto.analytics.AnalyticsEventDto dto = new com.carselling.oldcar.dto.analytics.AnalyticsEventDto();
            dto.setEventType((String) eventData.get("name")); // Assuming 'name' holds event type
            // Map other fields carefully. Mobile events might be "raw"
            // If timestamps are sent as longs:
            if (eventData.get("timestamp") instanceof Number) {
                dto.setClientTimestamp(java.time.LocalDateTime.ofInstant(
                        java.time.Instant.ofEpochMilli(((Number) eventData.get("timestamp")).longValue()),
                        java.time.ZoneId.systemDefault()));
            }

            // Extract arbitrary metadata
            dto.setMetadata(eventData);

            return dto;
        } catch (Exception e) {
            log.warn("Failed to map mobile event: {}", eventData, e);
            return null;
        }
    }

    /**
     * Extract user ID from authentication
     */
    private Long getUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return ((UserPrincipal) authentication.getPrincipal()).getId();
        }
        return null; // Anonymous user
    }
}
