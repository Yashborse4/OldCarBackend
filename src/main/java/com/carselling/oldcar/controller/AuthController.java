package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.auth.*;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication Controller with comprehensive endpoints
 * Handles user registration, login, password reset, and token management
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, String>>> healthCheck() {
        Map<String, String> healthData = Map.of(
                "status", "UP",
                "service", "Car Selling API",
                "version", "1.0.0"
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Service is healthy",
                "The authentication service is running normally",
                healthData
        ));
    }

    /**
     * Register a new user
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<JwtAuthResponse>> registerUser(
            @Valid @RequestBody RegisterRequest request) {
        
        log.info("User registration request for username: {}", request.getUsername());

        JwtAuthResponse authResponse = authService.registerUser(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "User registered successfully",
                        "Your account has been created successfully. You can now use the provided token for authentication.",
                        authResponse
                ));
    }

    /**
     * Login user
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtAuthResponse>> loginUser(
            @Valid @RequestBody LoginRequest request) {
        
        log.info("Login request for user: {}", request.getUsernameOrEmail());

        JwtAuthResponse authResponse = authService.loginUser(request);

        return ResponseEntity.ok(ApiResponse.success(
                "Login successful",
                "You have been successfully authenticated. Use the access token for API requests and the refresh token to get new access tokens.",
                authResponse
        ));
    }

    /**
     * Refresh access token
     * POST /api/auth/refresh-token
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<JwtAuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        
        log.info("Token refresh request");

        JwtAuthResponse authResponse = authService.refreshToken(request);

        return ResponseEntity.ok(ApiResponse.success(
                "Token refreshed successfully",
                "New access and refresh tokens have been generated.",
                authResponse
        ));
    }

    /**
     * Validate token
     * POST /api/auth/validate-token
     */
    @PostMapping("/validate-token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateToken(
            HttpServletRequest request) {
        
        String token = extractTokenFromRequest(request);
        if (!StringUtils.hasText(token)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(
                            "Token validation failed",
                            "No token provided in Authorization header"
                    ));
        }

        Map<String, Object> validationResult = authService.validateToken(token);
        boolean isValid = (Boolean) validationResult.get("valid");

        if (isValid) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Token validation completed",
                    "Token is valid and active",
                    validationResult
            ));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(
                            "Token validation failed",
                            "Token is invalid or expired",
                            validationResult
                    ));
        }
    }

    /**
     * Forgot password - initiate password reset
     * POST /api/auth/forgot-password
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Object>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        
        log.info("Forgot password request for username: {}", request.getUsername());

        authService.forgotPassword(request);

        return ResponseEntity.ok(ApiResponse.success(
                "Password reset initiated",
                "If the username exists, an OTP has been generated. Please check your email or contact support for the OTP."
        ));
    }

    /**
     * Reset password using OTP
     * POST /api/auth/reset-password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Object>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        
        log.info("Password reset request for username: {}", request.getUsername());

        authService.resetPassword(request);

        return ResponseEntity.ok(ApiResponse.success(
                "Password reset successful",
                "Your password has been reset successfully. You can now login with your new password."
        ));
    }

    /**
     * Logout (placeholder - JWT is stateless)
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Object>> logout(HttpServletRequest request) {
        log.info("Logout request");

        // In a stateless JWT system, logout is typically handled on the client side
        // by simply discarding the token. However, you could implement token blacklisting here.
        
        return ResponseEntity.ok(ApiResponse.success(
                "Logout successful",
                "You have been logged out successfully. Please discard your tokens."
        ));
    }

    /**
     * Check if username is available
     * GET /api/auth/check-username/{username}
     */
    @GetMapping("/check-username/{username}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkUsernameAvailability(
            @PathVariable String username) {
        
        log.debug("Checking username availability: {}", username);

        // This would typically be handled by a separate service method
        // For now, we'll use a simple approach
        boolean isAvailable = !authService.hasRole(null); // Placeholder logic
        
        Map<String, Boolean> availability = Map.of(
                "available", isAvailable,
                "username", username.equals(username) // Placeholder
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Username availability checked",
                isAvailable ? "Username is available" : "Username is already taken",
                availability
        ));
    }

    /**
     * Check if email is available
     * GET /api/auth/check-email/{email}
     */
    @GetMapping("/check-email/{email}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkEmailAvailability(
            @PathVariable String email) {
        
        log.debug("Checking email availability: {}", email);

        // This would typically be handled by a separate service method
        // For now, we'll use a simple approach
        boolean isAvailable = true; // Placeholder logic
        
        Map<String, Boolean> availability = Map.of(
                "available", isAvailable,
                "email", email.equals(email) // Placeholder
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Email availability checked",
                isAvailable ? "Email is available" : "Email is already registered",
                availability
        ));
    }

    // Private helper methods

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
