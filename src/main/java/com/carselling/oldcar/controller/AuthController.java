package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.auth.*;
import com.carselling.oldcar.model.OtpPurpose;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.AuthService;
import com.carselling.oldcar.annotation.RateLimit;


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
 * Authentication Controller - Aligned with API Requirements
 * Handles user authentication with device tracking, enhanced responses, and
 * validation
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

        private final AuthService authService;

        /**
         * User Registration
         * POST /api/auth/register
         */
        @PostMapping("/register")
        public ResponseEntity<ApiResponse<JwtAuthResponse>> register(
                        @Valid @RequestBody RegisterRequest request) {

                log.info("User registration request for username: {}", request.getUsername());

                JwtAuthResponse response = authService.registerUser(request);

                return ResponseEntity.status(HttpStatus.CREATED).body(
                                ApiResponse.success(
                                                "User registered successfully",
                                                "Account created successfully. Please verify your email.",
                                                response));
        }

        /**
         * User Login with Device Info
         * POST /api/auth/login
         */
        @PostMapping("/login")
        public ResponseEntity<ApiResponse<JwtAuthResponse>> login(
                        @Valid @RequestBody LoginRequest request) {

                log.debug("Login attempt for user: {}", request.getUsernameOrEmail());

                JwtAuthResponse authResponse = authService.loginUser(request);

                return ResponseEntity.ok(ApiResponse.success(
                                "Login successful",
                                "Authentication completed successfully",
                                authResponse));
        }

        /**
         * Token Refresh
         * POST /api/auth/refresh-token
         */
        @PostMapping("/refresh-token")
        public ResponseEntity<ApiResponse<JwtAuthResponse>> refreshToken(
                        @Valid @RequestBody RefreshTokenRequest request) {

                log.info("Token refresh request");

                JwtAuthResponse authResponse = authService.refreshToken(request);

                return ResponseEntity.ok(ApiResponse.success(
                                "Token refreshed successfully",
                                "New access and refresh tokens have been generated",
                                authResponse));
        }

        /**
         * Token Validation
         * POST /api/auth/validate-token
         */
        @PostMapping("/validate-token")
        public ResponseEntity<ApiResponse<TokenValidationResponse>> validateToken(
                        HttpServletRequest request) {

                if (request == null) {
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error(
                                                        "Token validation failed",
                                                        "Request object is null"));
                }

                String token = extractTokenFromRequest(request);
                if (!StringUtils.hasText(token)) {
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error(
                                                        "Token validation failed",
                                                        "No token provided in Authorization header"));
                }

                TokenValidationResponse validationResult = authService.validateToken(token);

                if (validationResult == null) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error(
                                                        "Token validation failed",
                                                        "Validation result is null"));
                }

                if (validationResult.isValid()) {
                        return ResponseEntity.ok(ApiResponse.success(
                                        "Token validation completed",
                                        "Token is valid and active",
                                        validationResult));
                } else {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(ApiResponse.error(
                                                        "Token validation failed",
                                                        "Token is invalid or expired"));
                }
        }

        /**
         * Reset Password - Confirm with OTP
         * POST /api/auth/password/reset
         */
        @PostMapping("/password/reset")
        public ResponseEntity<ApiResponse<Object>> resetPassword(
                        @Valid @RequestBody ResetPasswordRequest request) {

                log.info("Password reset request for username: {}", request.getUsername());

                authService.resetPassword(request);

                return ResponseEntity.ok(ApiResponse.success(
                                "Password reset successful",
                                "Your password has been reset successfully. You can now login with your new password."));
        }

        /**
         * Send OTP
         * POST /api/auth/otp/send
         */
        @PostMapping("/otp/send")
        @RateLimit(capacity = 3, refill = 1, refillPeriod = 5)
        public ResponseEntity<ApiResponse<String>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
                if (request == null) {
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error("Invalid request", "Request body is required"));
                }

                log.info("Request to send OTP for purpose: {}", request.getPurpose());

                OtpPurpose purpose = request.getPurpose();
                if (purpose == null) {
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error("Invalid OTP purpose", "OTP purpose is required"));
                }

                switch (purpose) {
                        case EMAIL_VERIFICATION -> authService.requestEmailVerificationOtp(request.getEmail());
                        case LOGIN -> authService.requestLoginOtp(request.getEmail());
                        case PASSWORD_RESET -> {
                                if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                                        return ResponseEntity.badRequest()
                                                        .body(ApiResponse.error("Invalid request", "Email is required for password reset"));
                                }
                                ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest();
                                forgotRequest.setUsername(request.getEmail());
                                authService.forgotPassword(forgotRequest);
                        }
                        default -> {
                                return ResponseEntity.badRequest()
                                                .body(ApiResponse.error("Unsupported OTP purpose", "Purpose " + purpose + " is not supported"));
                        }
                }

                return ResponseEntity.ok(ApiResponse.success(
                                "OTP sent successfully",
                                "Please check your email for the verification code"));
        }

        /**
         * Logout
         * POST /api/auth/logout
         */
        @PostMapping("/logout")
        public ResponseEntity<ApiResponse<Object>> logout(HttpServletRequest request) {
                if (request == null) {
                        log.warn("Logout request with null request object");
                        return ResponseEntity.ok(ApiResponse.success(
                                        "Logout successful",
                                        "No token provided for logout"));
                }

                String token = extractTokenFromRequest(request);
                log.info("Logout request");

                authService.logout(token);

                return ResponseEntity.ok(ApiResponse.success(
                                "Logout successful",
                                "You have been logged out successfully."));
        }

        /**
         * Check Username Availability
         * GET /api/auth/check-username/{username}
         */
        @GetMapping("/check-username/{username}")
        public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkUsernameAvailability(
                        @PathVariable String username) {

                log.info("Checking username availability: {}", username);

                boolean isAvailable = authService.isUsernameAvailable(username);
                Map<String, Boolean> result = Map.of("available", isAvailable);

                return ResponseEntity.ok(ApiResponse.success(
                                "Username availability checked",
                                isAvailable ? "Username is available" : "Username is already taken",
                                result));
        }

        /**
         * Check Email Availability
         * GET /api/auth/check-email/{email}
         */
        @GetMapping("/check-email/{email}")
        public ResponseEntity<ApiResponse<Map<String, Boolean>>> checkEmailAvailability(
                        @PathVariable String email) {

                log.info("Checking email availability: {}", email);

                boolean isAvailable = authService.isEmailAvailable(email);
                Map<String, Boolean> result = Map.of("available", isAvailable);

                return ResponseEntity.ok(ApiResponse.success(
                                "Email availability checked",
                                isAvailable ? "Email is available" : "Email is already registered",
                                result));
        }

        /**
         * Request Email Verification OTP
         * POST /api/auth/email/verify/request
         */
        @PostMapping("/login/otp/request")
        @RateLimit(capacity = 5, refill = 1, refillPeriod = 5)
        public ResponseEntity<ApiResponse<String>> requestLoginOtp(@Valid @RequestBody SendOtpRequest request) {
                String email = request.getEmail();
                if (!StringUtils.hasText(email)) {
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error("Email is required", "Email is required"));
                }

                authService.requestLoginOtp(email);

                return ResponseEntity.ok(ApiResponse.success(
                                "Login OTP sent successfully",
                                "Please check your email for the login verification code"));
        }

        /**
         * Confirm Email Verification with OTP
         * POST /api/auth/email/verify/confirm
         */
        @PostMapping("/email/verify/confirm")
        public ResponseEntity<ApiResponse<JwtAuthResponse>> confirmEmailVerification(
                        @RequestBody ConfirmEmailVerificationRequest request) {
                if (request == null) {
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error("Invalid request", "Request body is required"));
                }

                String email = request.getEmail();
                String otp = request.getOtp();

                if (!StringUtils.hasText(email) || !StringUtils.hasText(otp)) {
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error("Invalid request", "Email and OTP are required"));
                }

                JwtAuthResponse authResponse = authService.verifyEmailOtp(email, otp);

                if (authResponse == null) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error("Email verification failed", "Authentication response is null"));
                }

                return ResponseEntity.ok(ApiResponse.success(
                                "Email verified successfully",
                                "You have been logged in automatically.",
                                authResponse));
        }

        /**
         * Confirm Login with OTP
         * POST /api/auth/login/otp/confirm
         */
        @PostMapping("/login/otp/confirm")
        public ResponseEntity<ApiResponse<JwtAuthResponse>> confirmLoginOtp(
                        @Valid @RequestBody ConfirmLoginOtpRequest request) {
                if (request == null) {
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error("Invalid request", "Request body is required"));
                }

                String email = request.getEmail();
                String otp = request.getOtp();

                if (!StringUtils.hasText(email) || !StringUtils.hasText(otp)) {
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error("Invalid request", "Email and OTP are required"));
                }

                JwtAuthResponse response = authService.loginWithOtp(email, otp);

                if (response == null) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body(ApiResponse.error("Login OTP confirmation failed", "Authentication response is null"));
                }

                return ResponseEntity.ok(ApiResponse.success(
                                "Login successful",
                                "Authentication completed successfully",
                                response));
        }

        /**
         * Extract JWT token from Authorization header
         */
        private String extractTokenFromRequest(HttpServletRequest request) {
                if (request == null) {
                        return null;
                }
                String bearerToken = request.getHeader("Authorization");
                if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                        return bearerToken.substring(7);
                }
                return null;
        }
}
