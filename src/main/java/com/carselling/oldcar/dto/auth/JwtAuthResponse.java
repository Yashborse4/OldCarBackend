package com.carselling.oldcar.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JWT Authentication Response - Aligned with frontend requirements
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtAuthResponse {
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private Long userId;
    private String username;
    private String email;
    private String role;
    private String location;
    private Boolean emailVerified;
    private Boolean verifiedDealer;
    private String dealerStatus; // Added to support granular status display
    private LocalDateTime expiresAt;
    private LocalDateTime refreshExpiresAt;
    private Long expiresIn; // in seconds
    private Long refreshExpiresIn; // in seconds

    // Helper methods
    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.trim().isEmpty();
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isRefreshTokenExpired() {
        return refreshExpiresAt != null && refreshExpiresAt.isBefore(LocalDateTime.now());
    }
}
