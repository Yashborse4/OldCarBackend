package com.carselling.oldcar.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Google Social Login
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLoginRequest {

    @NotBlank(message = "Google ID Token is required")
    private String idToken;

    private String email;
    private String name;
    private String phoneNumber;

    private DeviceInfo deviceInfo;
}
