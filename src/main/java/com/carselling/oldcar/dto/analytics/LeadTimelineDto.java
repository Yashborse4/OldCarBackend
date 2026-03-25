package com.carselling.oldcar.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadTimelineDto {
    private String eventType;
    private String displayName;
    private String description;
    private String icon;
    private String color;
    private LocalDateTime timestamp;
    private String metadata; // JSON or formatted string
    private Long carId;
    private String carTitle;
    private Integer actionDurationSeconds;
    private String durationText;
}
