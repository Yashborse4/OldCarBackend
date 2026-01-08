package com.carselling.oldcar.dto.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for starting a new session
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionStartDto {

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

    @Size(max = 50)
    private String entryScreen;
}
