package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.auth.*;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.AuthServiceV2;
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
 * Enhanced Authentication Controller V2 - Aligned with API Requirements
 * Handles user authentication with device tracking, enhanced responses, and validation
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthControllerV2 {

    private final AuthServiceV2 authServiceV2;

    /**
     * User Registration
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        
        log.info("User registration request for username: {}", request.getUsername());

        RegisterResponse response = authServiceV2.registerUser(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "User registered successfully",
                        "Account created successfully. Please verify your email.",
                        response
                ));
    }

    /**
     * User Login with Device Info
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtAuthResponseV2>> login(
            @Valid @RequestBody LoginRequest request) {
        
        log.info("Login attempt for user: {}", request.getUsernameOrEmail());
        
        JwtAuthResponseV2 authResponse = authServiceV2.loginUser(request);

        return ResponseEntity.ok(ApiResponse.success(
                "Login successful",
                "Authentication completed successfully",
                authResponse
        ));
    }

    /**
     * Token Refresh
     * POST /api/auth/refresh-token
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<JwtAuthResponseV2>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        
        log.info("Token refresh request");

        JwtAuthResponseV2 authResponse = authServiceV2.refreshToken(request);

        return ResponseEntity.ok(ApiResponse.success(
                "Token refreshed successfully",
                "New access and refresh tokens have been generated",
                authResponse
        ));
    }

    /**
     * Token Validation
     * POST /api/auth/validate-token
     */
    @PostMapping("/validate-token")
    public ResponseEntity<ApiResponse<TokenValidationResponse>> validateToken(
            HttpServletRequest request) {
        
        String token = extractTokenFromRequest(request);
        if (!StringUtils.hasText(token)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(
                            "Token validation failed",
                            "No token provided in Authorization header"
                    ));
        }

        TokenValidationResponse validationResult = authServiceV2.validateToken(token);

        if (validationResult.isValid()) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Token validation completed",
                    "Token is valid and active",
                    validationResult
            ));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(
                            "Token validation failed",
                            "Token is invalid or expired"
                    ));
        }
    }

    /**
     * Forgot Password
     * POST /api/auth/forgot-password
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Object>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestV2 request) {
        
        log.info("Forgot password request for username: {}", request.getUsername());

        authServiceV2.forgotPassword(request);

        return ResponseEntity.ok(ApiResponse.success(
                "Password reset initiated",
                "If the username exists, an OTP has been generated. Please check your email or contact support for the OTP."
        ));
    }

    /**
     * Reset Password with OTP
     * POST /api/auth/reset-password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Object>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestV2 request) {
        
        log.info("Password reset request for username: {}", request.getUsername());

        authServiceV2.resetPassword(request);

        return ResponseEntity.ok(ApiResponse.success(
                "Password reset successful",
                "Your password has been reset successfully. You can now login with your new password."
        ));
    }

    /**
     * Logout
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Object>> logout(HttpServletRequest request) {
        String token = extractTokenFromRequest(request);
        log.info("Logout request");

        authServiceV2.logout(token);

        return ResponseEntity.ok(ApiResponse.success(
                "Logout successful",
                "You have been logged out successfully."
        ));
    }

    /**
     * Check Username Availability
     * GET /api/auth/check-username/{username}
     */
    @GetMapping("/check-username/{username}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkUsernameAvailability(
            @PathVariable String username) {
        
        log.info("Checking username availability: {}", username);

        boolean isAvailable = authServiceV2.isUsernameAvailable(username);
        Map<String, Boolean> result = Map.of("available", isAvailable);

        return ResponseEntity.ok(ApiResponse.success(
                "Username availability checked",
                isAvailable ? "Username is available" : "Username is already taken",
                result
        ));
    }

    /**
     * Check Email Availability
     * GET /api/auth/check-email/{email}
     */
    @GetMapping("/check-email/{email}")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkEmailAvailability(
            @PathVariable String email) {
        
        log.info("Checking email availability: {}", email);

        boolean isAvailable = authServiceV2.isEmailAvailable(email);
        Map<String, Boolean> result = Map.of("available", isAvailable);

        return ResponseEntity.ok(ApiResponse.success(
                "Email availability checked",
                isAvailable ? "Email is available" : "Email is already registered",
                result
        ));
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
