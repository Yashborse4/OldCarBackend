package com.carselling.oldcar.service;

import com.carselling.oldcar.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for Mobile App specific logic
 */
@Service
@Slf4j
public class MobileAppService {

    private final NotificationService notificationService;
    private final AdvancedSearchService advancedSearchService;

    public MobileAppService(
            NotificationService notificationService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) AdvancedSearchService advancedSearchService) {
        this.notificationService = notificationService;
        this.advancedSearchService = advancedSearchService;
    }

    public Map<String, Object> getVersionInfo(String appVersion, String platform) {
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
                "downloadUrl", getDownloadUrl(platform));
    }

    public void registerDevice(Long userId, String deviceToken) {
        log.info("Registering device for user: {}", userId);
        if (deviceToken != null && !deviceToken.isEmpty()) {
            notificationService.registerDevice(userId, deviceToken);
        }
    }

    public void unregisterDevice(Long userId, String deviceToken) {
        log.info("Unregistering device for user: {}", userId);
        if (deviceToken != null && !deviceToken.isEmpty()) {
            notificationService.unregisterDevice(userId, deviceToken);
        }
    }

    public Map<String, Object> getMobileConfig(String appVersion, String platform, String language) {
        Locale locale = Locale.forLanguageTag(language);

        return Map.of(
                "supportedLanguages", List.of("en", "es", "fr", "de"),
                "currentLanguage", locale.getLanguage(),
                "localizedStrings", getDefaultLocalizedStrings(),
                "vehicleAttributes", getDefaultVehicleAttributes(),
                "dateFormat", "MM/dd/yyyy",
                "features", getMobileFeatures(appVersion, platform),
                "apiEndpoints", getMobileApiEndpoints(),
                "cacheSettings", getCacheSettings(),
                "syncSettings", getSyncSettings());
    }

    public Map<String, Object> getSyncData(String userId, String lastSyncTimestamp) {
        // Simplified sync logic - mostly mocks for now as real implementation depends
        // on client DB schema
        return Map.of(
                "vehicles", getSyncVehicleData(userId, lastSyncTimestamp),
                "messages", List.of(), // Placeholder
                "notifications", List.of(), // Placeholder
                "userProfile", Map.of("id", userId), // Placeholder
                "syncTimestamp", System.currentTimeMillis(),
                "hasMoreData", false);
    }

    public Map<String, Object> getSearchSuggestions(String query, int limit) {
        // Return empty suggestions if AdvancedSearchService is not available (e.g., in
        // test contexts)
        if (advancedSearchService == null) {
            log.debug("AdvancedSearchService not available, returning empty suggestions");
            return Map.of(
                    "general", List.of(),
                    "makes", List.of(),
                    "popularSearches", List.of());
        }

        List<String> suggestions = advancedSearchService.suggest(query, limit)
                .stream()
                .map(doc -> doc.getBrand() + " " + doc.getModel())
                .collect(Collectors.toList());

        return Map.of(
                "general", suggestions,
                "makes",
                suggestions.stream().filter(s -> Character.isUpperCase(s.charAt(0))).collect(Collectors.toList()),
                "popularSearches", advancedSearchService.getTrendingSearchTerms(limit));
    }

    private int compareVersions(String version1, String version2) {
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int part1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int part2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (part1 < part2)
                return -1;
            if (part1 > part2)
                return 1;
        }
        return 0;
    }

    private String getDownloadUrl(String platform) {
        return platform.equalsIgnoreCase("iOS")
                ? "https://apps.apple.com/app/car-selling-platform"
                : "https://play.google.com/store/apps/details?id=com.carselling.app";
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
                "voiceSearch", compareVersions(appVersion, "2.1.0") >= 0);
    }

    private Map<String, Object> getMobileApiEndpoints() {
        return Map.of(
                "vehicles", "/api/vehicles",
                "auth", "/api/auth",
                "chat", "/api/chat",
                "notifications", "/api/notifications",
                "files", "/api/files",
                "mobile", "/api/mobile");
    }

    private Map<String, Object> getCacheSettings() {
        return Map.of(
                "vehicleListCacheDuration", 300000,
                "imagesCacheDuration", 3600000,
                "userProfileCacheDuration", 1800000,
                "searchResultsCacheDuration", 600000,
                "maxCacheSize", "50MB");
    }

    private Map<String, Object> getSyncSettings() {
        return Map.of(
                "syncInterval", 900000,
                "wifiOnlySync", false,
                "syncOnAppStart", true,
                "maxOfflineData", "100MB",
                "syncBatchSize", 50);
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
                Map.entry("location", "Location"));
    }

    private Map<String, Object> getDefaultVehicleAttributes() {
        return Map.of(
                "makes",
                List.of("Toyota", "Honda", "BMW", "Mercedes-Benz", "Audi", "Ford", "Chevrolet", "Volkswagen", "Nissan",
                        "Hyundai"),
                "bodyTypes",
                List.of("Sedan", "SUV", "Coupe", "Convertible", "Wagon", "Hatchback", "Pickup", "Van", "Crossover"),
                "fuelTypes", List.of("Gasoline", "Diesel", "Electric", "Hybrid", "Plug-in Hybrid", "CNG", "LPG"),
                "transmissions", List.of("Manual", "Automatic", "CVT", "Semi-Automatic"),
                "driveTypes", List.of("Front-Wheel Drive", "Rear-Wheel Drive", "All-Wheel Drive", "Four-Wheel Drive"),
                "conditions", List.of("New", "Like New", "Excellent", "Good", "Fair", "Poor"),
                "colors",
                List.of("Black", "White", "Silver", "Gray", "Red", "Blue", "Green", "Brown", "Gold", "Other"));
    }

    // Mocks for Sync Data
    private Object getSyncVehicleData(String userId, String lastSyncTimestamp) {
        return Map.of(
                "favorites", List.of(),
                "recentViews", List.of(),
                "savedSearches", List.of());
    }

}
