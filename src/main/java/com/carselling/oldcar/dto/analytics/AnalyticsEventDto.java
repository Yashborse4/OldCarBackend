package com.carselling.oldcar.dto.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for receiving analytics events from frontend
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsEventDto {

    @NotBlank(message = "Session ID is required")
    @Size(max = 100)
    private String sessionId;

    @NotBlank(message = "Event type is required")
    @Size(max = 50)
    private String eventType;

    @Size(max = 30)
    private String targetType;

    @Size(max = 50)
    private String targetId;

    private Map<String, Object> metadata;

    @Size(max = 50)
    private String screenName;

    @Size(max = 50)
    private String previousScreen;

    private Integer sessionDurationSeconds;
    private Integer actionDurationSeconds;

    /**
     * Client-side timestamp for offline event capture
     */
    private LocalDateTime clientTimestamp;
}
