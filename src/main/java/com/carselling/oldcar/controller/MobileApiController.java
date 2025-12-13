package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controller for Mobile App specific APIs
 */
@RestController
@RequestMapping("/api/mobile")
@RequiredArgsConstructor
@Slf4j
public class MobileApiController {

    /**
     * Check app version compatibility
     */
    @GetMapping("/version-check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkAppVersion(
            @RequestParam String appVersion,
            @RequestParam String platform) {
        
        log.info("Version check request: {} on {}", appVersion, platform);
        
        Map<String, Object> versionInfo = getVersionInfo(appVersion, platform);
        
        return ResponseEntity.ok(ApiResponse.success("Version check completed", versionInfo));
    }

    /**
     * Register device for push notifications
     */
    @PostMapping("/register-device")
    @PreAuthorize("hasRole('VIEWER')")
    public ResponseEntity<ApiResponse<String>> registerDevice(
            @RequestBody Map<String, String> deviceInfo,
            HttpServletRequest request) {
        
        String userId = getCurrentUserId();
        String deviceToken = deviceInfo.get("deviceToken");
        String deviceType = deviceInfo.get("deviceType"); // iOS, Android
        String appVersion = deviceInfo.get("appVersion");
        
        log.info("Registering device for user: {}, type: {}", userId, deviceType);
        
        // TODO: Implement device registration when NotificationService is available
        // For now, return success
        
        return ResponseEntity.ok(ApiResponse.success("Device registered successfully"));
    }

    /**
     * Unregister device from push notifications
     */
    @PostMapping("/unregister-device")
    @PreAuthorize("hasRole('VIEWER')")
    public ResponseEntity<ApiResponse<String>> unregisterDevice(
            @RequestParam String deviceToken) {
        
        String userId = getCurrentUserId();
        
        log.info("Unregistering device for user: {}", userId);
        
        // TODO: Implement device unregistration when NotificationService is available
        // For now, return success
        
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
        
        Locale locale = new Locale(language);
        
        Map<String, Object> config = Map.of(
            "supportedLanguages", List.of("en", "es", "fr", "de"),
            "currentLanguage", locale.getLanguage(),
            "localizedStrings", getDefaultLocalizedStrings(),
            "vehicleAttributes", getDefaultVehicleAttributes(),
            "dateFormat", "MM/dd/yyyy",
            "features", getMobileFeatures(appVersion, platform),
            "apiEndpoints", getMobileApiEndpoints(),
            "cacheSettings", getCacheSettings(),
            "syncSettings", getSyncSettings()
        );
        
        return ResponseEntity.ok(ApiResponse.success("Mobile configuration retrieved", config));
    }

    /**
     * Sync data for offline support
     */
    @GetMapping("/sync")
    @PreAuthorize("hasRole('VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncOfflineData(
            @RequestParam(required = false) String lastSyncTimestamp,
            @RequestParam(defaultValue = "all") String dataTypes) {
        
        String userId = getCurrentUserId();
        
        log.info("Syncing offline data for user: {}, lastSync: {}", userId, lastSyncTimestamp);
        
        Map<String, Object> syncData = Map.of(
            "vehicles", getSyncVehicleData(userId, lastSyncTimestamp),
            "messages", getSyncMessageData(userId, lastSyncTimestamp),
            "notifications", getSyncNotificationData(userId, lastSyncTimestamp),
            "userProfile", getSyncUserProfile(userId),
            "syncTimestamp", System.currentTimeMillis(),
            "hasMoreData", false
        );
        
        return ResponseEntity.ok(ApiResponse.success("Offline data synced", syncData));
    }

    /**
     * Report app usage analytics
     */
    @PostMapping("/analytics")
    @PreAuthorize("hasRole('VIEWER')")
    @Operation(summary = "Report app usage analytics", description = "Send mobile app usage analytics data")
    public ResponseEntity<ApiResponse<String>> reportAnalytics(
            @RequestBody Map<String, Object> analyticsData) {
        
        String userId = getCurrentUserId();
        
        log.info("Received analytics data from user: {}", userId);
        
        // Process analytics data
        processAnalyticsData(userId, analyticsData);
        
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
                "fileUpload", "up"
            ),
            "maintenance", false,
            "message", "All systems operational"
        );
        
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
        
        Map<String, Object> suggestions = Map.of(
            "makes", getMakeSuggestions(query, limit),
            "models", getModelSuggestions(query, limit),
            "locations", getLocationSuggestions(query, limit),
            "popularSearches", getPopularSearches(limit)
        );
        
        return ResponseEntity.ok(ApiResponse.success("Search suggestions retrieved", suggestions));
    }

    // Private helper methods

    private Map<String, Object> getVersionInfo(String appVersion, String platform) {
        // Version compatibility logic
        String minimumVersion = platform.equalsIgnoreCase("iOS") ? "1.0.0" : "1.0.0";
        String latestVersion = platform.equalsIgnoreCase("iOS") ? "2.1.0" : "2.1.0";
        
        boolean isSupported = compareVersions(appVersion, minimumVersion) >= 0;
        boolean updateAvailable = compareVersions(latestVersion, appVersion) > 0;
        boolean forceUpdate = compareVersions(appVersion, minimumVersion) < 0;
        
        return Map.of(
            "currentVersion", appVersion,
            "latestVersion", latestVersion,
            "minimumVersion", minimumVersion,
            "isSupported", isSupported,
            "updateAvailable", updateAvailable,
            "forceUpdate", forceUpdate,
            "updateMessage", updateAvailable ? "A new version is available with exciting features!" : null,
            "downloadUrl", getDownloadUrl(platform)
        );
    }

    private Map<String, Object> getMobileFeatures(String appVersion, String platform) {
        return Map.of(
            "pushNotifications", true,
            "offlineMode", true,
            "biometricAuth", compareVersions(appVersion, "1.5.0") >= 0,
            "darkMode", true,
            "multiLanguage", true,
            "advancedSearch", compareVersions(appVersion, "2.0.0") >= 0,
            "chatSupport", true,
            "socialLogin", true,
            "voiceSearch", compareVersions(appVersion, "2.1.0") >= 0
        );
    }

    private Map<String, Object> getMobileApiEndpoints() {
        return Map.of(
            "vehicles", "/api/vehicles",
            "auth", "/api/auth",
            "chat", "/api/chat",
            "notifications", "/api/notifications",
            "files", "/api/files",
            "mobile", "/api/mobile"
        );
    }

    private Map<String, Object> getCacheSettings() {
        return Map.of(
            "vehicleListCacheDuration", 300000,      // 5 minutes
            "imagesCacheDuration", 3600000,          // 1 hour
            "userProfileCacheDuration", 1800000,     // 30 minutes
            "searchResultsCacheDuration", 600000,    // 10 minutes
            "maxCacheSize", "50MB"
        );
    }

    private Map<String, Object> getSyncSettings() {
        return Map.of(
            "syncInterval", 900000,           // 15 minutes
            "wifiOnlySync", false,
            "syncOnAppStart", true,
            "maxOfflineData", "100MB",
            "syncBatchSize", 50
        );
    }

    private Object getSyncVehicleData(String userId, String lastSyncTimestamp) {
        // Mock implementation - replace with actual data sync logic
        return Map.of(
            "favorites", List.of(),
            "recentViews", List.of(),
            "savedSearches", List.of()
        );
    }

    private Object getSyncMessageData(String userId, String lastSyncTimestamp) {
        // Mock implementation - replace with actual message sync logic
        return List.of();
    }

    private Object getSyncNotificationData(String userId, String lastSyncTimestamp) {
        // Mock implementation - replace with actual notification sync logic
        return List.of();
    }

    private Object getSyncUserProfile(String userId) {
        // Mock implementation - replace with actual user profile sync logic
        return Map.of(
            "id", userId,
            "preferences", Map.of(),
            "settings", Map.of()
        );
    }

    private void processAnalyticsData(String userId, Map<String, Object> analyticsData) {
        // Process mobile analytics data
        log.info("Processing analytics data for user: {} - Events: {}", 
                userId, analyticsData.getOrDefault("events", "none"));
    }

    private List<String> getMakeSuggestions(String query, int limit) {
        // Mock implementation - replace with actual search logic
        return List.of("Toyota", "Honda", "BMW", "Mercedes", "Audi").stream()
                .filter(make -> make.toLowerCase().contains(query.toLowerCase()))
                .limit(limit)
                .toList();
    }

    private List<String> getModelSuggestions(String query, int limit) {
        // Mock implementation - replace with actual search logic
        return List.of("Camry", "Civic", "3 Series", "C-Class", "A4").stream()
                .filter(model -> model.toLowerCase().contains(query.toLowerCase()))
                .limit(limit)
                .toList();
    }

    private List<String> getLocationSuggestions(String query, int limit) {
        // Mock implementation - replace with actual location search logic
        return List.of("New York", "Los Angeles", "Chicago", "Houston", "Phoenix").stream()
                .filter(location -> location.toLowerCase().contains(query.toLowerCase()))
                .limit(limit)
                .toList();
    }

    private List<String> getPopularSearches(int limit) {
        return List.of("SUV under 20k", "Electric cars", "Toyota Camry", "BMW 3 Series", "Low mileage")
                .stream()
                .limit(limit)
                .toList();
    }

    private int compareVersions(String version1, String version2) {
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int part1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int part2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            
            if (part1 < part2) return -1;
            if (part1 > part2) return 1;
        }
        return 0;
    }

    private String getDownloadUrl(String platform) {
        return platform.equalsIgnoreCase("iOS") 
            ? "https://apps.apple.com/app/car-selling-platform" 
            : "https://play.google.com/store/apps/details?id=com.carselling.app";
    }

    private String getCurrentUserId() {
        // Mock implementation - replace with actual security context
        return "user123";
    }
    
    private Map<String, String> getDefaultLocalizedStrings() {
        return Map.ofEntries(
            Map.entry("welcome", "Welcome to Car Selling Platform"),
            Map.entry("search", "Search Vehicles"),
            Map.entry("login", "Log In"),
            Map.entry("register", "Sign Up"),
            Map.entry("logout", "Log Out"),
            Map.entry("profile", "My Profile"),
            Map.entry("settings", "Settings"),
            Map.entry("favorites", "My Favorites"),
            Map.entry("messages", "Messages"),
            Map.entry("notifications", "Notifications"),
            Map.entry("sell_car", "Sell Your Car"),
            Map.entry("buy_car", "Buy a Car"),
            Map.entry("price", "Price"),
            Map.entry("year", "Year"),
            Map.entry("make", "Make"),
            Map.entry("model", "Model"),
            Map.entry("mileage", "Mileage"),
            Map.entry("location", "Location")
        );
    }
    
    private Map<String, Object> getDefaultVehicleAttributes() {
        return Map.of(
            "makes", List.of("Toyota", "Honda", "BMW", "Mercedes-Benz", "Audi", "Ford", "Chevrolet", "Volkswagen", "Nissan", "Hyundai"),
            "bodyTypes", List.of("Sedan", "SUV", "Coupe", "Convertible", "Wagon", "Hatchback", "Pickup", "Van", "Crossover"),
            "fuelTypes", List.of("Gasoline", "Diesel", "Electric", "Hybrid", "Plug-in Hybrid", "CNG", "LPG"),
            "transmissions", List.of("Manual", "Automatic", "CVT", "Semi-Automatic"),
            "driveTypes", List.of("Front-Wheel Drive", "Rear-Wheel Drive", "All-Wheel Drive", "Four-Wheel Drive"),
            "conditions", List.of("New", "Like New", "Excellent", "Good", "Fair", "Poor"),
            "colors", List.of("Black", "White", "Silver", "Gray", "Red", "Blue", "Green", "Brown", "Gold", "Other")
        );
    }
}
