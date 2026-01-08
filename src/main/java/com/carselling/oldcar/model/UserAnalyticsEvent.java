package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity for tracking user behavior and analytics events.
 * Designed for high-volume, low-latency event ingestion.
 */
@Entity
@Table(name = "user_analytics_events", indexes = {
        @Index(name = "idx_analytics_user", columnList = "user_id"),
        @Index(name = "idx_analytics_session", columnList = "session_id"),
        @Index(name = "idx_analytics_event_type", columnList = "event_type"),
        @Index(name = "idx_analytics_target", columnList = "target_type, target_id"),
        @Index(name = "idx_analytics_created", columnList = "created_at"),
        @Index(name = "idx_analytics_screen", columnList = "screen_name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAnalyticsEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User who triggered the event (nullable for anonymous users)
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Session identifier (UUID generated on app start)
     */
    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    /**
     * Type of event
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    /**
     * Type of target entity (CAR, DEALER, SCREEN, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 30)
    private TargetType targetType;

    /**
     * ID of the target entity
     */
    @Column(name = "target_id", length = 50)
    private String targetId;

    /**
     * Additional event-specific metadata as JSON
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /**
     * Current screen name
     */
    @Column(name = "screen_name", length = 50)
    private String screenName;

    /**
     * Previous screen (for navigation tracking)
     */
    @Column(name = "previous_screen", length = 50)
    private String previousScreen;

    /**
     * Device type (ios, android, web)
     */
    @Column(name = "device_type", length = 20)
    private String deviceType;

    /**
     * App version string
     */
    @Column(name = "app_version", length = 20)
    private String appVersion;

    /**
     * OS version
     */
    @Column(name = "os_version", length = 30)
    private String osVersion;

    /**
     * Coarse location (city-level only for privacy)
     */
    @Column(name = "city", length = 100)
    private String city;

    /**
     * Seconds since session started
     */
    @Column(name = "session_duration")
    private Integer sessionDurationSeconds;

    /**
     * Duration of the specific action (e.g., view time in seconds)
     */
    @Column(name = "action_duration")
    private Integer actionDurationSeconds;

    /**
     * Event timestamp
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Client-side timestamp (for offline event capture)
     */
    @Column(name = "client_timestamp")
    private LocalDateTime clientTimestamp;

    // =============== ENUMS ===============

    /**
     * Types of analytics events
     */
    public enum EventType {
        // Car Interactions
        CAR_VIEW,
        CAR_VIEW_DURATION,
        CAR_SPECS_EXPAND,
        CAR_IMAGES_VIEW,
        CAR_IMAGES_SWIPE,
        CAR_SAVE,
        CAR_UNSAVE,
        CAR_SHARE,
        CAR_CONTACT_CLICK,
        CAR_CHAT_OPEN,
        CAR_CALL_CLICK,
        CAR_WHATSAPP_CLICK,
        CAR_COMPARE_ADD,
        CAR_TEST_DRIVE_REQUEST,

        // Navigation
        SCREEN_VIEW,
        SCREEN_EXIT,
        NAV_BACK,
        NAV_TAB_SWITCH,
        APP_OPEN,
        APP_CLOSE,
        APP_BACKGROUND,
        APP_FOREGROUND,

        // Search
        SEARCH_QUERY,
        SEARCH_FILTER_APPLY,
        SEARCH_FILTER_CLEAR,
        SEARCH_RESULT_CLICK,
        SEARCH_NO_RESULTS,
        SEARCH_SCROLL,

        // User Actions
        LOGIN,
        LOGOUT,
        SIGNUP,
        SIGNUP_STEP,
        PROFILE_VIEW,
        PROFILE_EDIT,
        SETTINGS_CHANGE,

        // Dealer Actions
        DEALER_VIEW,
        DEALER_CONTACT,
        DEALER_CARS_VIEW,

        // Funnels
        FUNNEL_START,
        FUNNEL_STEP,
        FUNNEL_DROP,
        FUNNEL_COMPLETE,

        // Engagement
        NOTIFICATION_RECEIVED,
        NOTIFICATION_OPEN,
        PUSH_TOKEN_REGISTERED,

        // Errors
        ERROR,
        CRASH
    }

    /**
     * Types of target entities
     */
    public enum TargetType {
        CAR,
        DEALER,
        USER,
        SCREEN,
        SEARCH,
        NOTIFICATION,
        CHAT,
        FUNNEL,
        OTHER
    }
}
