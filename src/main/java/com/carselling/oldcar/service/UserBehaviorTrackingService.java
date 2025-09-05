package com.carselling.oldcar.service;

import com.carselling.oldcar.entity.User;
import com.carselling.oldcar.entity.Vehicle;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * User Behavior Analytics & Personalization Service
 * Tracks user interactions and builds personalized experiences
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserBehaviorTrackingService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PerformanceMonitoringService performanceService;

    // Redis key patterns
    private static final String USER_VIEWS_KEY = "user:views:";
    private static final String USER_SEARCHES_KEY = "user:searches:";
    private static final String USER_FAVORITES_KEY = "user:favorites:";
    private static final String USER_INQUIRIES_KEY = "user:inquiries:";
    private static final String USER_CLICKS_KEY = "user:clicks:";
    private static final String USER_PREFERENCES_KEY = "user:preferences:";
    private static final String VEHICLE_VIEWS_KEY = "vehicle:views:";
    private static final String POPULAR_SEARCHES_KEY = "popular:searches";
    private static final String USER_SIMILARITY_KEY = "user:similarity:";
    private static final String RECOMMENDATION_VIEWS_KEY = "recommendation:views:";

    /**
     * Track vehicle view by user
     */
    @Async
    public void trackVehicleView(Long userId, Long vehicleId) {
        try {
            log.debug("Tracking vehicle view - User: {}, Vehicle: {}", userId, vehicleId);

            String timestamp = String.valueOf(System.currentTimeMillis());
            
            // Track user's view history with timestamps
            redisTemplate.opsForZSet().add(
                USER_VIEWS_KEY + userId, 
                vehicleId.toString(), 
                Double.parseDouble(timestamp)
            );
            
            // Track vehicle view count
            redisTemplate.opsForZSet().incrementScore(
                VEHICLE_VIEWS_KEY + "global", 
                vehicleId.toString(), 
                1.0
            );
            
            // Update user preferences based on viewed vehicle
            updateUserPreferencesFromView(userId, vehicleId);
            
            // Set expiration for user data (30 days)
            redisTemplate.expire(USER_VIEWS_KEY + userId, 30, TimeUnit.DAYS);
            
            log.debug("Successfully tracked vehicle view for user {} vehicle {}", userId, vehicleId);

        } catch (Exception e) {
            log.error("Error tracking vehicle view for user {} vehicle {}: {}", 
                    userId, vehicleId, e.getMessage(), e);
        }
    }

    /**
     * Track search query by user
     */
    @Async
    public void trackSearchQuery(Long userId, String query, Map<String, Object> filters) {
        try {
            log.debug("Tracking search query - User: {}, Query: {}", userId, query);

            String timestamp = String.valueOf(System.currentTimeMillis());
            
            // Store search with metadata
            SearchRecord searchRecord = new SearchRecord();
            searchRecord.setQuery(query);
            searchRecord.setFilters(filters);
            searchRecord.setTimestamp(LocalDateTime.now());
            
            // Add to user's search history
            redisTemplate.opsForZSet().add(
                USER_SEARCHES_KEY + userId, 
                searchRecord.toString(), 
                Double.parseDouble(timestamp)
            );
            
            // Track popular searches globally
            if (query != null && !query.trim().isEmpty()) {
                redisTemplate.opsForZSet().incrementScore(
                    POPULAR_SEARCHES_KEY, 
                    query.toLowerCase().trim(), 
                    1.0
                );
            }
            
            // Update user preferences based on search filters
            updateUserPreferencesFromSearch(userId, filters);
            
            // Set expiration for search data
            redisTemplate.expire(USER_SEARCHES_KEY + userId, 30, TimeUnit.DAYS);

        } catch (Exception e) {
            log.error("Error tracking search query for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Track user click on vehicle (from search results, recommendations, etc.)
     */
    @Async
    public void trackVehicleClick(Long userId, Long vehicleId, String context) {
        try {
            log.debug("Tracking vehicle click - User: {}, Vehicle: {}, Context: {}", 
                    userId, vehicleId, context);

            String timestamp = String.valueOf(System.currentTimeMillis());
            
            ClickRecord clickRecord = new ClickRecord();
            clickRecord.setVehicleId(vehicleId);
            clickRecord.setContext(context);
            clickRecord.setTimestamp(LocalDateTime.now());
            
            redisTemplate.opsForZSet().add(
                USER_CLICKS_KEY + userId, 
                clickRecord.toString(), 
                Double.parseDouble(timestamp)
            );
            
            // Track click-through rates by context
            redisTemplate.opsForHash().increment(
                "clickthrough:context", 
                context, 
                1
            );

        } catch (Exception e) {
            log.error("Error tracking vehicle click: {}", e.getMessage(), e);
        }
    }

    /**
     * Track user favorite/like action
     */
    @Async
    public void trackVehicleFavorite(Long userId, Long vehicleId, boolean isFavorite) {
        try {
            log.debug("Tracking favorite action - User: {}, Vehicle: {}, Favorite: {}", 
                    userId, vehicleId, isFavorite);

            if (isFavorite) {
                redisTemplate.opsForSet().add(USER_FAVORITES_KEY + userId, vehicleId.toString());
            } else {
                redisTemplate.opsForSet().remove(USER_FAVORITES_KEY + userId, vehicleId.toString());
            }
            
            // Update user preferences based on favorites
            if (isFavorite) {
                updateUserPreferencesFromFavorite(userId, vehicleId);
            }

        } catch (Exception e) {
            log.error("Error tracking favorite action: {}", e.getMessage(), e);
        }
    }

    /**
     * Track user inquiry about a vehicle
     */
    @Async
    public void trackVehicleInquiry(Long userId, Long vehicleId, String inquiryType) {
        try {
            log.debug("Tracking inquiry - User: {}, Vehicle: {}, Type: {}", 
                    userId, vehicleId, inquiryType);

            String timestamp = String.valueOf(System.currentTimeMillis());
            
            InquiryRecord inquiryRecord = new InquiryRecord();
            inquiryRecord.setVehicleId(vehicleId);
            inquiryRecord.setInquiryType(inquiryType);
            inquiryRecord.setTimestamp(LocalDateTime.now());
            
            redisTemplate.opsForZSet().add(
                USER_INQUIRIES_KEY + userId, 
                inquiryRecord.toString(), 
                Double.parseDouble(timestamp)
            );
            
            // Strong signal for preferences
            updateUserPreferencesFromInquiry(userId, vehicleId);

        } catch (Exception e) {
            log.error("Error tracking inquiry: {}", e.getMessage(), e);
        }
    }

    /**
     * Track recommendation views for ML feedback
     */
    @Async
    public void trackRecommendationView(Long userId, List<Long> recommendedVehicleIds) {
        try {
            log.debug("Tracking recommendation views - User: {}, Count: {}", 
                    userId, recommendedVehicleIds.size());

            String timestamp = String.valueOf(System.currentTimeMillis());
            
            for (Long vehicleId : recommendedVehicleIds) {
                redisTemplate.opsForZSet().add(
                    RECOMMENDATION_VIEWS_KEY + userId, 
                    vehicleId.toString(), 
                    Double.parseDouble(timestamp)
                );
            }

        } catch (Exception e) {
            log.error("Error tracking recommendation views: {}", e.getMessage(), e);
        }
    }

    /**
     * Get user's vehicle preferences based on behavior
     */
    @Cacheable(value = "userPreferences", key = "#userId")
    public MachineLearningRecommendationService.VehiclePreferences getUserPreferences(Long userId) {
        try {
            log.debug("Getting user preferences for user: {}", userId);

            // Get cached preferences if available
            Object cachedPrefs = redisTemplate.opsForValue().get(USER_PREFERENCES_KEY + userId);
            if (cachedPrefs instanceof MachineLearningRecommendationService.VehiclePreferences) {
                return (MachineLearningRecommendationService.VehiclePreferences) cachedPrefs;
            }

            // Build preferences from user behavior
            MachineLearningRecommendationService.VehiclePreferences preferences = 
                    buildPreferencesFromBehavior(userId);
            
            // Cache preferences for 1 hour
            redisTemplate.opsForValue().set(
                USER_PREFERENCES_KEY + userId, 
                preferences, 
                1, TimeUnit.HOURS
            );

            return preferences;

        } catch (Exception e) {
            log.error("Error getting user preferences for user {}: {}", userId, e.getMessage(), e);
            return createDefaultPreferences();
        }
    }

    /**
     * Get user's search history
     */
    public List<String> getSearchHistory(Long userId) {
        try {
            Set<ZSetOperations.TypedTuple<Object>> searchHistory = 
                    redisTemplate.opsForZSet().reverseRangeWithScores(
                            USER_SEARCHES_KEY + userId, 0, 49); // Last 50 searches

            return searchHistory.stream()
                    .map(tuple -> extractQueryFromSearchRecord(tuple.getValue().toString()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting search history for user {}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Get user's view history
     */
    public List<Vehicle> getViewHistory(Long userId) {
        try {
            Set<ZSetOperations.TypedTuple<Object>> viewHistory = 
                    redisTemplate.opsForZSet().reverseRangeWithScores(
                            USER_VIEWS_KEY + userId, 0, 49); // Last 50 views

            List<Long> vehicleIds = viewHistory.stream()
                    .map(tuple -> Long.parseLong(tuple.getValue().toString()))
                    .collect(Collectors.toList());

            return vehicleRepository.findAllById(vehicleIds);

        } catch (Exception e) {
            log.error("Error getting view history for user {}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Get user's favorite vehicles
     */
    public List<Vehicle> getFavoriteVehicles(Long userId) {
        try {
            Set<Object> favoriteIds = redisTemplate.opsForSet().members(USER_FAVORITES_KEY + userId);
            
            if (favoriteIds == null || favoriteIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<Long> vehicleIds = favoriteIds.stream()
                    .map(id -> Long.parseLong(id.toString()))
                    .collect(Collectors.toList());

            return vehicleRepository.findAllById(vehicleIds);

        } catch (Exception e) {
            log.error("Error getting favorite vehicles for user {}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Find users with similar behavior patterns
     */
    @Cacheable(value = "similarUsers", key = "#userId + '_' + #limit")
    public List<Long> findSimilarUsers(Long userId, int limit) {
        try {
            log.debug("Finding similar users for user: {}", userId);

            // Get user's view and favorite patterns
            Set<Object> userViews = redisTemplate.opsForZSet().range(USER_VIEWS_KEY + userId, 0, -1);
            Set<Object> userFavorites = redisTemplate.opsForSet().members(USER_FAVORITES_KEY + userId);
            
            if ((userViews == null || userViews.isEmpty()) && 
                (userFavorites == null || userFavorites.isEmpty())) {
                return Collections.emptyList();
            }

            // Find other users who viewed/liked similar vehicles
            Map<Long, Double> userSimilarityScores = new HashMap<>();
            
            // Check view similarity
            if (userViews != null) {
                for (Object vehicleId : userViews) {
                    Set<Object> otherUsers = redisTemplate.opsForSet().members(
                        "vehicle:viewers:" + vehicleId);
                    if (otherUsers != null) {
                        for (Object otherUserId : otherUsers) {
                            Long otherUserIdLong = Long.parseLong(otherUserId.toString());
                            if (!otherUserIdLong.equals(userId)) {
                                userSimilarityScores.merge(otherUserIdLong, 1.0, Double::sum);
                            }
                        }
                    }
                }
            }

            // Check favorite similarity (higher weight)
            if (userFavorites != null) {
                for (Object vehicleId : userFavorites) {
                    Set<Object> otherUsers = redisTemplate.opsForSet().members(
                        "vehicle:favoriters:" + vehicleId);
                    if (otherUsers != null) {
                        for (Object otherUserId : otherUsers) {
                            Long otherUserIdLong = Long.parseLong(otherUserId.toString());
                            if (!otherUserIdLong.equals(userId)) {
                                userSimilarityScores.merge(otherUserIdLong, 3.0, Double::sum);
                            }
                        }
                    }
                }
            }

            // Return top similar users
            return userSimilarityScores.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .limit(limit)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error finding similar users for user {}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Get personalized user analytics
     */
    public UserAnalytics getUserAnalytics(Long userId) {
        try {
            log.debug("Generating user analytics for user: {}", userId);

            UserAnalytics analytics = new UserAnalytics();
            analytics.setUserId(userId);
            analytics.setAnalysisDate(LocalDateTime.now());

            // View analytics
            Long viewCount = redisTemplate.opsForZSet().zCard(USER_VIEWS_KEY + userId);
            analytics.setTotalViews(viewCount != null ? viewCount.intValue() : 0);
            
            // Search analytics
            Long searchCount = redisTemplate.opsForZSet().zCard(USER_SEARCHES_KEY + userId);
            analytics.setTotalSearches(searchCount != null ? searchCount.intValue() : 0);
            
            // Favorite analytics
            Long favoriteCount = redisTemplate.opsForSet().size(USER_FAVORITES_KEY + userId);
            analytics.setTotalFavorites(favoriteCount != null ? favoriteCount.intValue() : 0);
            
            // Inquiry analytics
            Long inquiryCount = redisTemplate.opsForZSet().zCard(USER_INQUIRIES_KEY + userId);
            analytics.setTotalInquiries(inquiryCount != null ? inquiryCount.intValue() : 0);

            // Recent activity (last 7 days)
            long weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
            Long recentViews = redisTemplate.opsForZSet().count(USER_VIEWS_KEY + userId, weekAgo, Double.MAX_VALUE);
            analytics.setRecentViews(recentViews != null ? recentViews.intValue() : 0);

            // Engagement score
            analytics.setEngagementScore(calculateEngagementScore(analytics));
            
            // User preferences summary
            MachineLearningRecommendationService.VehiclePreferences prefs = getUserPreferences(userId);
            analytics.setPreferredMakes(prefs.getPreferredMakes());
            analytics.setBudgetRange(prefs.getMinPrice() + " - " + prefs.getMaxPrice());

            return analytics;

        } catch (Exception e) {
            log.error("Error generating user analytics for user {}: {}", userId, e.getMessage(), e);
            return createDefaultAnalytics(userId);
        }
    }

    /**
     * Get trending search terms
     */
    @Cacheable(value = "trendingSearches", key = "#limit")
    public List<String> getTrendingSearchTerms(int limit) {
        try {
            Set<ZSetOperations.TypedTuple<Object>> trending = 
                    redisTemplate.opsForZSet().reverseRangeWithScores(POPULAR_SEARCHES_KEY, 0, limit - 1);

            return trending.stream()
                    .map(tuple -> tuple.getValue().toString())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error getting trending search terms: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // Private helper methods

    private void updateUserPreferencesFromView(Long userId, Long vehicleId) {
        try {
            Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
            if (vehicle == null) return;

            // Update user's viewed brands
            redisTemplate.opsForZSet().incrementScore(
                "user:brands:" + userId, 
                vehicle.getMake(), 
                1.0
            );
            
            // Track vehicle viewers for similarity
            redisTemplate.opsForSet().add("vehicle:viewers:" + vehicleId, userId.toString());

        } catch (Exception e) {
            log.error("Error updating preferences from view: {}", e.getMessage(), e);
        }
    }

    private void updateUserPreferencesFromSearch(Long userId, Map<String, Object> filters) {
        try {
            if (filters == null) return;

            // Update preferences based on search filters
            if (filters.containsKey("make")) {
                String make = filters.get("make").toString();
                redisTemplate.opsForZSet().incrementScore(
                    "user:brands:" + userId, 
                    make, 
                    2.0 // Higher weight for explicit search
                );
            }

            if (filters.containsKey("minPrice") && filters.containsKey("maxPrice")) {
                redisTemplate.opsForHash().put(
                    "user:budget:" + userId, 
                    "minPrice", 
                    filters.get("minPrice").toString()
                );
                redisTemplate.opsForHash().put(
                    "user:budget:" + userId, 
                    "maxPrice", 
                    filters.get("maxPrice").toString()
                );
            }

        } catch (Exception e) {
            log.error("Error updating preferences from search: {}", e.getMessage(), e);
        }
    }

    private void updateUserPreferencesFromFavorite(Long userId, Long vehicleId) {
        try {
            Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
            if (vehicle == null) return;

            // Strong preference signal
            redisTemplate.opsForZSet().incrementScore(
                "user:brands:" + userId, 
                vehicle.getMake(), 
                5.0
            );
            
            // Track vehicle favoriters for similarity
            redisTemplate.opsForSet().add("vehicle:favoriters:" + vehicleId, userId.toString());

        } catch (Exception e) {
            log.error("Error updating preferences from favorite: {}", e.getMessage(), e);
        }
    }

    private void updateUserPreferencesFromInquiry(Long userId, Long vehicleId) {
        try {
            Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
            if (vehicle == null) return;

            // Very strong preference signal
            redisTemplate.opsForZSet().incrementScore(
                "user:brands:" + userId, 
                vehicle.getMake(), 
                10.0
            );

        } catch (Exception e) {
            log.error("Error updating preferences from inquiry: {}", e.getMessage(), e);
        }
    }

    private MachineLearningRecommendationService.VehiclePreferences buildPreferencesFromBehavior(Long userId) {
        MachineLearningRecommendationService.VehiclePreferences prefs = 
                new MachineLearningRecommendationService.VehiclePreferences();

        try {
            // Get preferred makes from behavior
            Set<ZSetOperations.TypedTuple<Object>> topBrands = 
                    redisTemplate.opsForZSet().reverseRangeWithScores("user:brands:" + userId, 0, 4);
            
            List<String> preferredMakes = topBrands.stream()
                    .map(tuple -> tuple.getValue().toString())
                    .collect(Collectors.toList());
            prefs.setPreferredMakes(preferredMakes);

            // Get budget range from searches
            Map<Object, Object> budget = redisTemplate.opsForHash().entries("user:budget:" + userId);
            if (budget.containsKey("minPrice") && budget.containsKey("maxPrice")) {
                try {
                    prefs.setMinPrice(new java.math.BigDecimal(budget.get("minPrice").toString()));
                    prefs.setMaxPrice(new java.math.BigDecimal(budget.get("maxPrice").toString()));
                } catch (NumberFormatException e) {
                    // Use defaults
                }
            }

            // Infer other preferences from viewed vehicles
            List<Vehicle> viewedVehicles = getViewHistory(userId);
            if (!viewedVehicles.isEmpty()) {
                OptionalInt avgYear = viewedVehicles.stream()
                        .mapToInt(Vehicle::getYear)
                        .max();
                if (avgYear.isPresent()) {
                    prefs.setMinYear(Math.max(2000, avgYear.getAsInt() - 10));
                }
            }

        } catch (Exception e) {
            log.error("Error building preferences from behavior: {}", e.getMessage(), e);
        }

        return prefs;
    }

    private MachineLearningRecommendationService.VehiclePreferences createDefaultPreferences() {
        MachineLearningRecommendationService.VehiclePreferences prefs = 
                new MachineLearningRecommendationService.VehiclePreferences();
        prefs.setPreferredMakes(List.of("Toyota", "Honda", "Ford"));
        return prefs;
    }

    private String extractQueryFromSearchRecord(String searchRecord) {
        // Simple extraction - in production would use proper JSON parsing
        try {
            if (searchRecord.contains("query:")) {
                return searchRecord.split("query:")[1].split(",")[0].trim();
            }
        } catch (Exception e) {
            log.debug("Error extracting query from search record: {}", e.getMessage());
        }
        return null;
    }

    private double calculateEngagementScore(UserAnalytics analytics) {
        // Simple engagement score calculation
        double score = 0.0;
        
        score += analytics.getTotalViews() * 1.0;
        score += analytics.getTotalSearches() * 2.0;
        score += analytics.getTotalFavorites() * 5.0;
        score += analytics.getTotalInquiries() * 10.0;
        
        // Recent activity boost
        score += analytics.getRecentViews() * 2.0;
        
        return Math.min(score, 100.0); // Cap at 100
    }

    private UserAnalytics createDefaultAnalytics(Long userId) {
        UserAnalytics analytics = new UserAnalytics();
        analytics.setUserId(userId);
        analytics.setTotalViews(0);
        analytics.setTotalSearches(0);
        analytics.setTotalFavorites(0);
        analytics.setTotalInquiries(0);
        analytics.setRecentViews(0);
        analytics.setEngagementScore(0.0);
        analytics.setAnalysisDate(LocalDateTime.now());
        return analytics;
    }

    // Data classes for behavior tracking

    public static class SearchRecord {
        private String query;
        private Map<String, Object> filters;
        private LocalDateTime timestamp;

        // Getters and setters
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        
        public Map<String, Object> getFilters() { return filters; }
        public void setFilters(Map<String, Object> filters) { this.filters = filters; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        @Override
        public String toString() {
            return "query:" + query + ",filters:" + (filters != null ? filters.toString() : "{}") + 
                   ",timestamp:" + timestamp;
        }
    }

    public static class ClickRecord {
        private Long vehicleId;
        private String context;
        private LocalDateTime timestamp;

        // Getters and setters
        public Long getVehicleId() { return vehicleId; }
        public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
        
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        @Override
        public String toString() {
            return "vehicleId:" + vehicleId + ",context:" + context + ",timestamp:" + timestamp;
        }
    }

    public static class InquiryRecord {
        private Long vehicleId;
        private String inquiryType;
        private LocalDateTime timestamp;

        // Getters and setters
        public Long getVehicleId() { return vehicleId; }
        public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
        
        public String getInquiryType() { return inquiryType; }
        public void setInquiryType(String inquiryType) { this.inquiryType = inquiryType; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        @Override
        public String toString() {
            return "vehicleId:" + vehicleId + ",inquiryType:" + inquiryType + ",timestamp:" + timestamp;
        }
    }

    public static class UserAnalytics {
        private Long userId;
        private int totalViews;
        private int totalSearches;
        private int totalFavorites;
        private int totalInquiries;
        private int recentViews;
        private double engagementScore;
        private List<String> preferredMakes;
        private String budgetRange;
        private LocalDateTime analysisDate;

        // Getters and setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        
        public int getTotalViews() { return totalViews; }
        public void setTotalViews(int totalViews) { this.totalViews = totalViews; }
        
        public int getTotalSearches() { return totalSearches; }
        public void setTotalSearches(int totalSearches) { this.totalSearches = totalSearches; }
        
        public int getTotalFavorites() { return totalFavorites; }
        public void setTotalFavorites(int totalFavorites) { this.totalFavorites = totalFavorites; }
        
        public int getTotalInquiries() { return totalInquiries; }
        public void setTotalInquiries(int totalInquiries) { this.totalInquiries = totalInquiries; }
        
        public int getRecentViews() { return recentViews; }
        public void setRecentViews(int recentViews) { this.recentViews = recentViews; }
        
        public double getEngagementScore() { return engagementScore; }
        public void setEngagementScore(double engagementScore) { this.engagementScore = engagementScore; }
        
        public List<String> getPreferredMakes() { return preferredMakes; }
        public void setPreferredMakes(List<String> preferredMakes) { this.preferredMakes = preferredMakes; }
        
        public String getBudgetRange() { return budgetRange; }
        public void setBudgetRange(String budgetRange) { this.budgetRange = budgetRange; }
        
        public LocalDateTime getAnalysisDate() { return analysisDate; }
        public void setAnalysisDate(LocalDateTime analysisDate) { this.analysisDate = analysisDate; }
    }
}
