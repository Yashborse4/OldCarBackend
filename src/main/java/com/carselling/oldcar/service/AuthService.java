package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.auth.*;
import com.carselling.oldcar.exception.AuthenticationFailedException;
import com.carselling.oldcar.exception.InvalidInputException;
import com.carselling.oldcar.exception.ResourceNotFoundException;

import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.DealerStatus;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.security.jwt.JwtTokenProvider;
import com.carselling.oldcar.util.SecurityUtils;
import com.carselling.oldcar.model.OtpPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.security.SecureRandom;

/**
 * Authentication Service - Aligned with API Requirements
 * Handles user authentication with device tracking and enhanced features
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    private static final int GENERATED_PASSWORD_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Register a new user
     */
    @Transactional
    public JwtAuthResponse registerUser(RegisterRequest request) {
        if (request == null) {
            throw new InvalidInputException("Registration request cannot be null");
        }

        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new InvalidInputException("Email is required for registration");
        }

        String username = request.getUsername();
        if (username == null || username.trim().isEmpty()) {
            // Extract username from email using utility method
            String email = request.getEmail();
            username = SecurityUtils.extractUsernameFromEmail(email);
            if (username == null) {
                throw new InvalidInputException("Invalid email format provided");
            }
            log.info("No username provided, extracting from email: {} -> {}", email, username);
            request.setUsername(username); // Update request object
        }
        log.info("Registering new user: {}", username);

        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        String requestedRole = request.getRole();
        Role role = Role.USER;
        if (requestedRole != null && !requestedRole.trim().isEmpty()) {
            String roleValue = requestedRole.trim().toUpperCase();
            if (!"USER".equals(roleValue) && !"DEALER".equals(roleValue)) {
                throw new InvalidInputException("Invalid role. Allowed roles are USER or DEALER");
            }
            role = Role.valueOf(roleValue);
        }

        String rawPassword = request.getPassword();
        if (rawPassword != null) {
            rawPassword = rawPassword.trim();
        }
        if (rawPassword == null || rawPassword.isEmpty()) {
            rawPassword = generateRandomPassword(GENERATED_PASSWORD_LENGTH);
            log.info("No password provided for user {}. Generated a secure random password.", username);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .dealerStatus(role == Role.DEALER ? DealerStatus.UNVERIFIED : null)
                .isActive(true)
                .isEmailVerified(false) // In production, send verification email
                .isAccountNonLocked(true)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        user = userRepository.save(user);

        // Send verification OTP once
        otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.EMAIL_VERIFICATION);

        // DO NOT generate tokens here. Enforce email verification flow.
        return JwtAuthResponse.builder()
                .accessToken(null)
                .refreshToken(null)
                .tokenType(null)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .location(user.getLocation())
                .emailVerified(Boolean.TRUE.equals(user.getIsEmailVerified()))
                .verifiedDealer(Boolean.TRUE.equals(user.getDealerStatus() == DealerStatus.VERIFIED))
                .expiresAt(null)
                .refreshExpiresAt(null)
                .expiresIn(900L)
                .refreshExpiresIn(604800L)
                .build();
    }

    private String generateRandomPassword(int length) {
        StringBuilder builder = new StringBuilder(length);
        int charsLength = PASSWORD_CHARS.length();
        for (int i = 0; i < length; i++) {
            int index = SECURE_RANDOM.nextInt(charsLength);
            builder.append(PASSWORD_CHARS.charAt(index));
        }
        return builder.toString();
    }

    /**
     * Login user with device info tracking
     */
    public JwtAuthResponse loginUser(LoginRequest request) {
        if (request == null) {
            throw new InvalidInputException("Login request cannot be null");
        }

        if (request.getUsernameOrEmail() == null || request.getUsernameOrEmail().trim().isEmpty()) {
            throw new InvalidInputException("Username or email is required for login");
        }

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new InvalidInputException("Password is required for login");
        }

        log.info("Login attempt for user: {}", request.getUsernameOrEmail());

        User user = userRepository.findByUsernameOrEmail(request.getUsernameOrEmail(), request.getUsernameOrEmail())
                .orElseThrow(() -> new AuthenticationFailedException("User not found"));

        if (!user.isAccountNonLocked()) {
            throw new AuthenticationFailedException("Account is locked due to multiple failed login attempts");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleFailedLogin(user);
            throw new AuthenticationFailedException("Invalid credentials");
        }

        if (Boolean.FALSE.equals(user.getIsEmailVerified())) {
            otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.EMAIL_VERIFICATION);
            log.info("Email not verified for user: {}. Sent verification OTP.", user.getUsername());

            throw new AuthenticationFailedException(
                    "Email is not verified. A verification code has been sent to your email.");
        }

        // Update last login time and device info
        updateLoginStats(user, request.getDeviceInfo());

        // Generate tokens
        return generateAuthResponse(user);
    }

    /**
     * Request OTP for Email Verification
     */
    public void requestEmailVerificationOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new IllegalArgumentException("Email is already verified");
        }

        otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.EMAIL_VERIFICATION);
    }

    /**
     * Verify Email with OTP and return auth tokens
     */
    public JwtAuthResponse verifyEmailOtp(String email, String otp) {
        boolean isValid = otpService.validateOtp(email, otp, OtpPurpose.EMAIL_VERIFICATION.name());
        if (!isValid) {
            throw new AuthenticationFailedException("Invalid or expired OTP");
        }

        // User status update is handled in OtpService.validateOtp ->
        // markEmailAsVerified
        // Fetch refreshed user to ensure we have updated state
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Generate tokens for auto-login
        return generateAuthResponse(user);
    }

    /**
     * Request OTP for Login
     */
    public void requestLoginOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isAccountNonLocked()) {
            throw new AuthenticationFailedException("Account is locked");
        }

        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new AuthenticationFailedException("Email is not verified");
        }

        otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.LOGIN);
    }

    /**
     * Login with OTP
     */
    public JwtAuthResponse loginWithOtp(String email, String otp) {
        boolean isValid = otpService.validateOtp(email, otp, OtpPurpose.LOGIN.name());
        if (!isValid) {
            throw new AuthenticationFailedException("Invalid or expired OTP");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check account lock status again just in case
        if (!user.isAccountNonLocked()) {
            throw new AuthenticationFailedException("Account is locked");
        }

        // Update stats
        updateLoginStats(user, null);

        // Generate tokens
        return generateAuthResponse(user);
    }

    private void updateLoginStats(User user, DeviceInfo deviceInfo) {
        user.setLastLoginAt(LocalDateTime.now());
        if (deviceInfo != null) {
            user.setLastLoginDevice(deviceInfo.getPlatform());
        }

        // Reset failed attempts if any
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setAccountLockedAt(null);
        }

        userRepository.save(user);
    }

    private JwtAuthResponse generateAuthResponse(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(900); // 15 minutes
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusSeconds(604800); // 7 days

        return JwtAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .location(user.getLocation())
                .emailVerified(Boolean.TRUE.equals(user.getIsEmailVerified()))
                .verifiedDealer(user.getDealerStatus() == DealerStatus.VERIFIED)
                .expiresAt(expiresAt)
                .refreshExpiresAt(refreshExpiresAt)
                .expiresIn(900L)
                .refreshExpiresIn(604800L)
                .build();
    }

    /**
     * Refresh JWT token
     */
    @Transactional
    public JwtAuthResponse refreshToken(RefreshTokenRequest request) {
        if (request == null) {
            throw new InvalidInputException("Refresh token request cannot be null");
        }

        if (request.getRefreshToken() == null || request.getRefreshToken().trim().isEmpty()) {
            throw new InvalidInputException("Refresh token is required");
        }

        log.info("Token refresh request");

        // Check if token is blacklisted (already used)
        if (jwtTokenProvider.isTokenBlacklisted(request.getRefreshToken())) {
            throw new AuthenticationFailedException("Refresh token has been revoked");
        }

        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new AuthenticationFailedException("Invalid refresh token");
        }

        if (!jwtTokenProvider.isRefreshToken(request.getRefreshToken())) {
            throw new AuthenticationFailedException("Token is not a refresh token");
        }

        // Blacklist the old refresh token to prevent reuse
        jwtTokenProvider.blacklistToken(request.getRefreshToken());

        Long userId = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Generate new tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(900);
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusSeconds(604800);

        return JwtAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .location(user.getLocation())
                .emailVerified(Boolean.TRUE.equals(user.getIsEmailVerified()))
                .verifiedDealer(user.getDealerStatus() == DealerStatus.VERIFIED)
                .expiresAt(expiresAt)
                .refreshExpiresAt(refreshExpiresAt)
                .expiresIn(900L)
                .refreshExpiresIn(604800L)
                .build();
    }

    /**
     * Validate token
     */
    public TokenValidationResponse validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return TokenValidationResponse.builder()
                    .valid(false)
                    .build();
        }

        boolean isValid = jwtTokenProvider.validateToken(token);

        if (!isValid) {
            return TokenValidationResponse.builder()
                    .valid(false)
                    .build();
        }

        try {
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            TokenValidationResponse.UserDetails userDetails = TokenValidationResponse.UserDetails.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .location(user.getLocation())
                    .emailVerified(Boolean.TRUE.equals(user.getIsEmailVerified()))
                    .verifiedDealer(user.getDealerStatus() == DealerStatus.VERIFIED)
                    .build();

            return TokenValidationResponse.builder()
                    .valid(true)
                    .userDetails(userDetails)
                    .build();
        } catch (Exception e) {
            return TokenValidationResponse.builder()
                    .valid(false)
                    .build();
        }
    }

    /**
     * Forgot password - generate OTP
     */
    public void forgotPassword(ForgotPasswordRequest request) {
        log.info("Forgot password request for username: {}", request.getUsername());

        // Find user by username or email to allow password reset via either
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            // Also try to find by email in case the user provided email instead of username
            userOpt = userRepository.findByEmail(request.getUsername());
        }

        if (userOpt.isEmpty()) {
            log.warn("Forgot password request for non-existent user: {}", request.getUsername());
            // Don't reveal if username/email exists - return successfully to prevent
            // enumeration
            return;
        }

        User user = userOpt.get();

        // Use OtpService to generate and send OTP
        otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.PASSWORD_RESET);
    }

    /**
     * Reset password with OTP
     */
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Password reset request for user: {}", request.getUsername());

        // Find user by username or email to allow password reset via either
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(request.getUsername());
        }

        if (userOpt.isEmpty()) {
            // Don't reveal if user exists - throw generic exception
            throw new AuthenticationFailedException("Invalid credentials");
        }

        User user = userOpt.get();

        // Validate OTP using OtpService
        boolean isValid = otpService.validateOtp(user.getEmail(), request.getOtp(), "PASSWORD_RESET");

        if (!isValid) {
            throw new AuthenticationFailedException("Invalid or expired OTP");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());

        // Reset account lock if any
        user.setAccountNonLocked(true);
        user.setFailedLoginAttempts(0);
        user.setAccountLockedAt(null);

        userRepository.save(user);

        log.info("Password reset successful for user: {}", request.getUsername());
    }

    /**
     * Logout (token blacklisting could be implemented here)
     */
    public void logout(String token) {
        log.info("Logout request");
        if (token == null || token.trim().isEmpty()) {
            log.warn("Logout requested without a valid token");
            return;
        }

        jwtTokenProvider.blacklistToken(token);
    }

    /**
     * Check username availability
     */
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    /**
     * Check email availability
     */
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    /**
     * Handle failed login attempt
     */
    private void handleFailedLogin(User user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

        if (user.getFailedLoginAttempts() >= 5) {
            user.setAccountNonLocked(false);
            user.setAccountLockedAt(LocalDateTime.now());
        }

        userRepository.save(user);
    }

    /**
     * Get current authenticated user
     */
    public User getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new AuthenticationFailedException("No authenticated user found");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /**
     * Get current authenticated user or null if anonymous
     */
    public User getCurrentUserOrNull() {
        try {
            return getCurrentUser();
        } catch (AuthenticationFailedException | ResourceNotFoundException e) {
            return null;
        }
    }

    /**
     * Check if current user has specific role
     */
    public boolean hasRole(Role role) {
        try {
            User currentUser = getCurrentUser();
            return currentUser.getRole() == role;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if the current authenticated user is the owner of the resource with the
     * given ID
     */
    public boolean isOwner(Long userId) {
        try {
            User currentUser = getCurrentUser();
            return currentUser.getId().equals(userId);
        } catch (Exception e) {
            return false;
        }
    }

}
