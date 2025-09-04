package com.carselling.oldcar.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Comprehensive JWT Authentication Response DTO
 * Contains both access and refresh tokens with user details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwtAuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long userId;
    private String username;
    private String email;
    private String role;
    private String location;
    
    // Token expiration information
    private LocalDateTime expiresAt;
    private LocalDateTime refreshExpiresAt;
    private Long expiresIn; // seconds until access token expires
    private Long refreshExpiresIn; // seconds until refresh token expires

    // Backward compatibility constructor
    public JwtAuthResponse(String accessToken, String tokenType, Long userId, String username, String email) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.userId = userId;
        this.username = username;
        this.email = email;
    }

    // Enhanced constructor
    public JwtAuthResponse(String accessToken, String refreshToken, String tokenType, 
                          Long userId, String username, String email, String role, String location,
                          LocalDateTime expiresAt, LocalDateTime refreshExpiresAt, 
                          Long expiresIn, Long refreshExpiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.role = role;
        this.location = location;
        this.expiresAt = expiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
        this.expiresIn = expiresIn;
        this.refreshExpiresIn = refreshExpiresIn;
    }

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
