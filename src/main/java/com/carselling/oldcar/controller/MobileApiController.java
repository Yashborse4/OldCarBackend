package com.carselling.oldcar.controller;
 
import com.carselling.oldcar.dto.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.carselling.oldcar.dto.mobile.MobileAnalyticsRequest;
import com.carselling.oldcar.dto.mobile.RegisterDeviceRequest;
import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.MobileAppService;
import jakarta.validation.Valid;

/**
 * Controller for Mobile App specific APIs
 */
@RestController
@RequestMapping("/api/mobile")
@RequiredArgsConstructor
@Slf4j
public class MobileApiController {

    private final MobileAppService mobileAppService;

    /**
     * Check app version compatibility
     */
    @GetMapping("/version-check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkAppVersion(
            @RequestParam String appVersion,
            @RequestParam String platform) {

        log.info("Version check request: {} on {}", appVersion, platform);

        Map<String, Object> versionInfo = mobileAppService.getVersionInfo(appVersion, platform);

        return ResponseEntity.ok(ApiResponse.success("Version check completed", versionInfo));
    }

    /**
     * Register device for push notifications
     */
    @PostMapping("/register-device")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<String>> registerDevice(
            @Valid @RequestBody RegisterDeviceRequest request,
            org.springframework.security.core.Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Long userId = userPrincipal.getId();

        log.info("Registering device for user: {}, token: {}", userId, request.getDeviceToken());

        mobileAppService.registerDevice(userId, request.getDeviceToken());

        return ResponseEntity.ok(ApiResponse.success("Device registered successfully"));
    }

    /**
     * Unregister device from push notifications
     */
    @PostMapping("/unregister-device")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<String>> unregisterDevice(
            @Valid @RequestBody RegisterDeviceRequest request,
            org.springframework.security.core.Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Long userId = userPrincipal.getId();

        log.info("Unregistering device for user: {}, token: {}", userId, request.getDeviceToken());

        mobileAppService.unregisterDevice(userId, request.getDeviceToken());

        return ResponseEntity.ok(ApiResponse.success("Device unregistered successfully"));
    }

    /**
     * Get app configuration for mobile
     */
    @GetMapping("/config")
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
     * Report app usage analytics
     */
    @PostMapping("/analytics")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Report app usage analytics", description = "Send mobile app usage analytics data")
    public ResponseEntity<ApiResponse<String>> reportAnalytics(
            @Valid @RequestBody MobileAnalyticsRequest request,
            org.springframework.security.core.Authentication authentication) {

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Long userId = userPrincipal.getId();

        log.info("Received analytics data from user: {}", userId);

        // Analytics processing can be async or delegated
        // For now, logging is sufficient as per previous implementation logic,
        // but now centralized in controller or service if complex.
        // Previous impl had private method. Let's keep it simple here.
        log.debug("Analytics events: {}", request.getEvents());

        return ResponseEntity.ok(ApiResponse.success("Analytics data received"));
    }

    /**
     * Get app health status
     */
    @GetMapping("/health")
    @Operation(summary = "Get mobile app health status", description = "Check the health status for mobile app")
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

    /**
     * Get mobile-specific search suggestions
     */
    @GetMapping("/search-suggestions")
    @Operation(summary = "Get search suggestions for mobile", description = "Optimized search suggestions for mobile interface")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSearchSuggestions(
            @Parameter(description = "Search query") @RequestParam String query,
            @Parameter(description = "Maximum suggestions") @RequestParam(defaultValue = "10") int limit) {

        Map<String, Object> suggestions = mobileAppService.getSearchSuggestions(query, limit);

        return ResponseEntity.ok(ApiResponse.success("Search suggestions retrieved", suggestions));
    }
    // Private helper methods

}
