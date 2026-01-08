package com.carselling.oldcar.dto.mobile;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDeviceRequest {
    @NotBlank(message = "Device token is required")
    private String deviceToken;
    private String platform;
    private String deviceId;
}

