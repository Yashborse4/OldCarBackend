package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.analytics.AnalyticsBatchDto;
import com.carselling.oldcar.dto.analytics.SessionStartDto;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.UserAnalyticsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for analytics event ingestion.
 * Designed for high-volume, low-latency operations.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class UserAnalyticsController {

    private final UserAnalyticsService analyticsService;

    /**
     * Start a new session
     * POST /api/analytics/session/start
     */
    @PostMapping("/session/start")
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
            return ResponseEntity.status(403).body(ApiResponse.error("Access denied", "Only the car owner can view insights"));
        }

        Map<String, Object> insights = analyticsService.getCarInsights(carId);

        return ResponseEntity.ok(ApiResponse.success(
                "Car insights retrieved",
                "Analytics for car " + carId,
                insights));
    }

    /**
     * Extract user ID from authentication
     */
    private Long getUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getId();
        }
        return null; // Anonymous user
    }
}
