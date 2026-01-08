package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Entity for tracking user sessions.
 * A session starts when app opens and ends when app closes or times out.
 */
@Entity
@Table(name = "user_sessions", indexes = {
        @Index(name = "idx_session_user", columnList = "user_id"),
        @Index(name = "idx_session_started", columnList = "started_at"),
        @Index(name = "idx_session_active", columnList = "is_active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {

    @Id
    @Column(name = "session_id", length = 100)
    private String sessionId;

    /**
     * User associated with this session
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Session start time
     */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /**
     * Session end time (null if active)
     */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /**
     * Total session duration in seconds
     */
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    /**
     * Is session currently active
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Device type
     */
    @Column(name = "device_type", length = 20)
    private String deviceType;

    /**
     * Device model
     */
    @Column(name = "device_model", length = 50)
    private String deviceModel;

    /**
     * App version
     */
    @Column(name = "app_version", length = 20)
    private String appVersion;

    /**
     * OS version
     */
    @Column(name = "os_version", length = 30)
    private String osVersion;

    /**
     * Coarse location (city)
     */
    @Column(name = "city", length = 100)
    private String city;

    /**
     * Total events in this session
     */
    @Column(name = "event_count")
    @Builder.Default
    private Integer eventCount = 0;

    /**
     * Total screens viewed
     */
    @Column(name = "screen_count")
    @Builder.Default
    private Integer screenCount = 0;

    /**
     * Total cars viewed
     */
    @Column(name = "cars_viewed")
    @Builder.Default
    private Integer carsViewed = 0;

    /**
     * List of screens visited (for path analysis)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "screens_visited", columnDefinition = "jsonb")
    private List<String> screensVisited;

    /**
     * Entry point screen
     */
    @Column(name = "entry_screen", length = 50)
    private String entryScreen;

    /**
     * Exit screen
     */
    @Column(name = "exit_screen", length = 50)
    private String exitScreen;

    /**
     * Session metadata
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /**
     * Increment event count
     */
    public void incrementEventCount() {
        this.eventCount = (this.eventCount == null ? 0 : this.eventCount) + 1;
    }

    /**
     * Increment screen count
     */
    public void incrementScreenCount() {
        this.screenCount = (this.screenCount == null ? 0 : this.screenCount) + 1;
    }

    /**
     * Increment cars viewed
     */
    public void incrementCarsViewed() {
        this.carsViewed = (this.carsViewed == null ? 0 : this.carsViewed) + 1;
    }

    /**
     * End the session
     */
    public void endSession() {
        this.endedAt = LocalDateTime.now();
        this.isActive = false;
        if (this.startedAt != null && this.endedAt != null) {
            this.durationSeconds = (int) java.time.Duration.between(startedAt, endedAt).getSeconds();
        }
    }
}
