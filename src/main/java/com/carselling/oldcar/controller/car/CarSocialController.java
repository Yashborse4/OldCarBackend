package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.TrackCarShareRequest;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.annotation.RateLimit;
import com.carselling.oldcar.service.car.CarService;
import com.carselling.oldcar.service.CarInteractionEventService;
import com.carselling.oldcar.model.CarInteractionEvent;
import com.carselling.oldcar.model.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.carselling.oldcar.util.SecurityUtils;
import java.util.Map;

/**
 * Controller for Car Social Interactions (Views, Shares, Events)
 */
@RestController
@RequestMapping("/api/cars")
@RequiredArgsConstructor
@Slf4j
public class CarSocialController {

    private final CarService carService;
    private final CarInteractionEventService carInteractionEventService;
    private final com.carselling.oldcar.service.ReferralService referralService;

    /**
     * Generate Signed Share Link
     * POST /api/cars/{id}/share-link
     */
    @PostMapping("/{id}/share-link")
    @RateLimit(capacity = 30, refill = 10, refillPeriod = 1)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, String>>> generateShareLink(
            @PathVariable String id,
            Authentication authentication) {

        Long currentUserId = SecurityUtils.getCurrentUserId();

        // Security check: ensure user has access to car (read access is enough usually,
        // but let's say anyone can share)
        // If strict: if (!carService.canView(id, currentUserId)) throw ...

        String token = referralService.generateReferralToken(Long.parseLong(id), currentUserId);

        // Construct full URL (frontend URL usually, but here returning token/params)
        // Frontend will append ?ref=token

        return ResponseEntity.ok(ApiResponse.success(
                "Share link generated",
                Map.of("token", token, "carId", id)));
    }

    /**
     * Track Vehicle View
     * POST /api/cars/{id}/view
     */
    @PostMapping("/{id}/view")
    @RateLimit(capacity = 120, refill = 60, refillPeriod = 1)
    public ResponseEntity<ApiResponse<Object>> trackVehicleView(
            @PathVariable String id,
            @RequestParam(required = false) String refToken,
            Authentication authentication) {
        log.info("Tracking view for vehicle: {} (Ref: {})", id, refToken != null ? "PRESENT" : "NONE");

        Long carId = Long.parseLong(id);
        Long currentUserId = null;
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            currentUserId = ((User) authentication.getPrincipal()).getId();
        }

        Long validReferrerId = null;
        if (refToken != null) {
            // Need car owner ID for strict check
            Long ownerId = carService.getCarOwnerId(id); // Ensure this method exists or fetch car lightweight
            validReferrerId = referralService.validateReferral(carId, refToken, currentUserId, ownerId);

            if (validReferrerId != null) {
                log.info("Valid referral detected: User {} referred by {}", currentUserId, validReferrerId);
            } else {
                log.warn("Invalid/Expired/Abusive referral token for car {}", id);
            }
        }

        // Pass validReferrerId to service (overloading existing method or passing via
        // context/metadata)
        // Ideally update CarService.trackVehicleView to accept referrerId, OR just
        // track event directly here

        // For now, standard tracking + event with strict referrer
        carService.trackVehicleView(id);

        // If we have a valid referrer, strictly track that event for attribution
        if (validReferrerId != null) {
            carInteractionEventService.trackEvent(
                    carId,
                    currentUserId,
                    CarInteractionEvent.EventType.CAR_VIEW,
                    null, null, null,
                    validReferrerId.toString(), // Validated Referrer ID stored in referrer field
                    "{\"verified_referral\": true}");
        }

        return ResponseEntity.ok(ApiResponse.success(
                "View tracked successfully",
                "Vehicle view has been recorded"));
    }

    /**
     * Track Vehicle Share
     * POST /api/cars/{id}/share
     */
    @PostMapping("/{id}/share")
    @RateLimit(capacity = 60, refill = 30, refillPeriod = 1)
    public ResponseEntity<ApiResponse<Object>> trackVehicleShare(
            @PathVariable String id,
            @Valid @RequestBody TrackCarShareRequest shareRequest) {

        log.info("Tracking share for vehicle: {} on platform: {}", id, shareRequest.getPlatform());

        String platform = shareRequest.getPlatform();
        carService.trackVehicleShare(id, platform);

        return ResponseEntity.ok(ApiResponse.success(
                "Share tracked successfully",
                String.format("Vehicle share on %s has been recorded", platform)));
    }

    /**
     * Track any car interaction event
     * POST /api/cars/events
     */
    @PostMapping("/events")
    @RateLimit(capacity = 60, refill = 30, refillPeriod = 1)
    public ResponseEntity<ApiResponse<Object>> trackCarEvent(
            @Valid @RequestBody com.carselling.oldcar.dto.car.CarInteractionEventDto eventDto,
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest request) {

        log.info("Tracking {} event for car {}", eventDto.getEventType(), eventDto.getCarId());

        CarInteractionEvent.EventType eventType = eventDto.getEventTypeEnum();
        if (eventType == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Invalid event type",
                    "Valid types: CAR_VIEW, CONTACT_CLICK, SAVE, SHARE, CHAT_OPEN"));
        }

        Long userId = null;
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            userId = ((User) authentication.getPrincipal()).getId();
        }

        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        carInteractionEventService.trackEvent(
                eventDto.getCarId(),
                userId,
                eventType,
                eventDto.getSessionId(),
                userAgent,
                ipAddress,
                eventDto.getReferrer(),
                eventDto.getMetadata());

        return ResponseEntity.ok(ApiResponse.success(
                "Event tracked",
                eventType.getDisplayName() + " event recorded"));
    }

    /**
     * Get event statistics for a car
     * GET /api/cars/{id}/events/stats
     */
    @GetMapping("/{id}/events/stats")
    @PreAuthorize("hasRole('DEALER') or hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getCarEventStats(@PathVariable String id) {
        log.info("Getting event stats for car {}", id);

        Long carId = Long.parseLong(id);
        Map<String, Long> stats = carInteractionEventService.getCarEventStats(carId);

        return ResponseEntity.ok(ApiResponse.success(
                "Event stats retrieved",
                "Statistics for car " + id,
                stats));
    }
}
