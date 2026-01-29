package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.mobile.RegisterDeviceRequest;
import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Register device for push notifications")
    public ResponseEntity<ApiResponse<String>> registerDevice(
            @Valid @RequestBody RegisterDeviceRequest request,
            Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Long userId = userPrincipal.getId();

        log.info("Registering device for user: {}, token: {}", userId, request.getDeviceToken());

        notificationService.registerDevice(userId, request.getDeviceToken());

        return ResponseEntity.ok(ApiResponse.success("Device registered successfully"));
    }

    /**
     * Unregister device from push notifications
     */
    @PostMapping("/devices/unregister")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Unregister device from push notifications")
    public ResponseEntity<ApiResponse<String>> unregisterDevice(
            @Valid @RequestBody RegisterDeviceRequest request,
            Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Long userId = userPrincipal.getId();

        log.info("Unregistering device for user: {}, token: {}", userId, request.getDeviceToken());

        notificationService.unregisterDevice(userId, request.getDeviceToken());

        return ResponseEntity.ok(ApiResponse.success("Device unregistered successfully"));
    }
}
