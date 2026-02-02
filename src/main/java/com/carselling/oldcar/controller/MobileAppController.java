package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.MobileAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for Mobile App lifecycle and configuration.
 * Handles version checks, configuration, health, and offline sync.
 */
@RestController
@RequestMapping("/api/v1/mobile")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mobile App Lifecycle", description = "Mobile app configuration and lifecycle management")
public class MobileAppController {

    private final MobileAppService mobileAppService;

    /**
     * Check app version compatibility
     */
    @GetMapping("/version-check")
    @Operation(summary = "Check app version compatibility")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkAppVersion(
            @RequestParam String appVersion,
            @RequestParam String platform) {

        log.info("Version check request: {} on {}", appVersion, platform);

        Map<String, Object> versionInfo = mobileAppService.getVersionInfo(appVersion, platform);

        return ResponseEntity.ok(ApiResponse.success("Version check completed", versionInfo));
    }

    /**
     * Get app configuration for mobile
     */
    @GetMapping("/config")
    @Operation(summary = "Get app configuration for mobile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMobileConfig(
            @RequestParam String appVersion,
            @RequestParam String platform,
            @RequestParam(defaultValue = "en") String language) {

        Map<String, Object> config = mobileAppService.getMobileConfig(appVersion, platform, language);

        return ResponseEntity.ok(ApiResponse.success("Mobile configuration retrieved", config));
    }

    /**
     * Sync data for offline support
     */
    @GetMapping("/sync")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Sync data for offline support")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncOfflineData(
            @RequestParam(required = false) String lastSyncTimestamp,
            @RequestParam(defaultValue = "all") String dataTypes,
            org.springframework.security.core.Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Long userId = userPrincipal.getId();

        log.info("Syncing offline data for user: {}, lastSync: {}", userId, lastSyncTimestamp);

        Map<String, Object> syncData = mobileAppService.getSyncData(userId.toString(), lastSyncTimestamp);

        return ResponseEntity.ok(ApiResponse.success("Offline data synced", syncData));
    }

    /**
     * Get app health status
     */
    @GetMapping("/health")
    @Operation(summary = "Get mobile app health status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealthStatus() {

        Map<String, Object> healthStatus = Map.of(
                "status", "healthy",
                "timestamp", System.currentTimeMillis(),
                "services", Map.of(
                        "api", "up",
                        "database", "up",
                        "notifications", "up",
                        "fileUpload", "up"),
                "maintenance", false,
                "message", "All systems operational");

        return ResponseEntity.ok(ApiResponse.success("Health check completed", healthStatus));
    }
}
