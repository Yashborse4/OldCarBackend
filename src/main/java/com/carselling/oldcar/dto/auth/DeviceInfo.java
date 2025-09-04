package com.carselling.oldcar.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Device Information DTO for authentication and tracking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInfo {
    private String platform;    // android, ios, web
    private String version;     // OS version
    private String deviceId;    // unique device identifier
    private String appVersion;  // app version
    private String deviceModel; // device model
    private String locale;      // device locale
}
