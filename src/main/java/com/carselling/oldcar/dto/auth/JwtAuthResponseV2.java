package com.carselling.oldcar.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JWT Authentication Response V2 - Aligned with API Requirements
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtAuthResponseV2 {
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private Long userId;
    private String username;
    private String email;
    private String role;
    private String location;
    private LocalDateTime expiresAt;
    private LocalDateTime refreshExpiresAt;
    private Long expiresIn;        // in seconds
    private Long refreshExpiresIn; // in seconds
}
