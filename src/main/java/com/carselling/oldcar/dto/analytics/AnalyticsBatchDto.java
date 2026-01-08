package com.carselling.oldcar.dto.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for batch ingesting analytics events (up to 100 events per request)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsBatchDto {

    @NotBlank(message = "Session ID is required")
    @Size(max = 100)
    private String sessionId;

    @Size(max = 20)
    private String deviceType;

    @Size(max = 50)
    private String deviceModel;

    @Size(max = 20)
    private String appVersion;

    @Size(max = 30)
    private String osVersion;

    @Size(max = 100)
    private String city;

    @NotEmpty(message = "Events list cannot be empty")
    @Size(max = 100, message = "Maximum 100 events per batch")
    @Valid
    private List<AnalyticsEventDto> events;
}
