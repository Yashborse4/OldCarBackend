package com.carselling.oldcar.dto.mobile;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileAnalyticsRequest {
    @NotNull(message = "Events are required")
    private List<Map<String, Object>> events;
    private String appVersion;
    private String platform;
    private String deviceId;
}

