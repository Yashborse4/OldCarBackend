package com.carselling.oldcar.controller;

import com.carselling.oldcar.annotation.RateLimit;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.mobile.RegisterDeviceRequest;
import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.NotificationService;
import com.carselling.oldcar.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for Notification preferences and device management.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "Notification preferences and device management")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Register device for push notifications
     */
    @PostMapping("/devices/register")
    @PreAuthorize("isAuthenticated()") // Changed from hasRole('USER') to allow all authenticated
    @RateLimit(capacity = 5, refill = 1, refillPeriod = 60) // 5 per minute
    @Operation(summary = "Register device for push notifications")
    public ResponseEntity<ApiResponse<String>> registerDevice(
            @Valid @RequestBody RegisterDeviceRequest request,
            Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Long userId = userPrincipal.getId();

        // Extract platform if available in request extended DTO, or query param, or
        // default
        // Assuming RegisterDeviceRequest might not have platform yet.
        // For now default to ANDROID if not present.
        String platform = "ANDROID";
        // If you update DTO later, use request.getPlatform()

        log.info("Registering device for user: {}, token: {}", userId, request.getDeviceToken());

        notificationService.registerToken(userId, request.getDeviceToken(), platform);

        return ResponseEntity.ok(ApiResponse.success("Device registered successfully"));
    }

    /**
     * New Endpoint: Register with explicit platform support
     */
    @PostMapping("/register-token")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Register device token with platform", description = "Register FCM token")
    public ResponseEntity<ApiResponse<Void>> registerTokenWithPlatform(
            @RequestParam String token,
            @RequestParam(defaultValue = "ANDROID") String platform) {

        Long userId = SecurityUtils.getCurrentUserId();
        notificationService.registerToken(userId, token, platform);

        return ResponseEntity.ok(ApiResponse.success("Device registered successfully", null));
    }

    /**
     * Unregister device from push notifications
     */
    @PostMapping("/devices/unregister")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Unregister device from push notifications")
    public ResponseEntity<ApiResponse<String>> unregisterDevice(
            @Valid @RequestBody RegisterDeviceRequest request,
            Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Long userId = userPrincipal.getId();

        log.info("Unregistering device for user: {}, token: {}", userId, request.getDeviceToken());

        notificationService.unregisterToken(request.getDeviceToken());

        return ResponseEntity.ok(ApiResponse.success("Device unregistered successfully"));
    }

    // --- Admin Endpoints for Testing ---

    @PostMapping("/test-send")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(capacity = 10, refill = 1, refillPeriod = 1) // 10 per second
    @Operation(summary = "Send test notification", description = "Send a test push notification to a specific user")
    public ResponseEntity<ApiResponse<Void>> sendTestNotification(
            @RequestParam Long userId,
            @RequestBody Map<String, String> payload,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        String title = payload.getOrDefault("title", "Test Notification");
        String body = payload.getOrDefault("body", "This is a test notification from Admin.");

        notificationService.queuePush(userId, title, body, payload, idempotencyKey);

        return ResponseEntity.ok(ApiResponse.success("Test notification sent", null));
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    @RateLimit(capacity = 1, refill = 1, refillPeriod = 60) // 1 broadcast per minute
    @Operation(summary = "Broadcast notification", description = "Send notification to ALL users")
    public ResponseEntity<ApiResponse<Void>> broadcastNotification(
            @RequestBody Map<String, String> payload) {

        String title = payload.getOrDefault("title", "Broadcast");
        String body = payload.getOrDefault("body", "Message to all users");

        notificationService.sendToAll(title, body);

        return ResponseEntity.ok(ApiResponse.success("Broadcast sent", null));
    }
}
