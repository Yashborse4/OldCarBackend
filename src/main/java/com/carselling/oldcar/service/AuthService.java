package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.auth.*;
import com.carselling.oldcar.exception.AuthenticationFailedException;
import com.carselling.oldcar.exception.InvalidInputException;
import com.carselling.oldcar.exception.ResourceAlreadyExistsException;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.Otp;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.OtpRepository;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.Random;

/**
 * Authentication Service with comprehensive business logic
 * Handles user registration, login, password reset, and token management
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    // Constants
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int ACCOUNT_LOCK_DURATION_MINUTES = 30;
    private static final int OTP_VALIDITY_MINUTES = 15;
    private static final int MAX_OTP_REQUESTS_PER_HOUR = 5;

    /**
     * Register a new user
     */
    public JwtAuthResponse registerUser(RegisterRequest request) {
        log.info("Attempting to register user with username: {}", request.getUsername());

        // Check if user already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResourceAlreadyExistsException("User", "username", request.getUsername());
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceAlreadyExistsException("User", "email", request.getEmail());
        }

        // Create new user
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(StringUtils.hasText(request.getRole()) ? 
                      Role.fromString(request.getRole()) : Role.VIEWER)
                .location(request.getLocation())
                .phoneNumber(request.getPhoneNumber())
                .isActive(true)
                .isEmailVerified(false)
                .failedLoginAttempts(0)
                .build();

        try {
            user = userRepository.save(user);
            log.info("Successfully registered user: {}", user.getUsername());

            // Generate tokens for immediate login after registration
            return generateTokenResponse(user);

        } catch (Exception e) {
            log.error("Error registering user: {}", request.getUsername(), e);
            throw new RuntimeException("Failed to register user", e);
        }
    }

    /**
     * Authenticate user and return JWT tokens
     */
    public JwtAuthResponse loginUser(LoginRequest request) {
        log.info("Attempting login for user: {}", request.getUsernameOrEmail());

        // Find user
        User user = userRepository.findByUsernameOrEmail(request.getUsernameOrEmail(), request.getUsernameOrEmail())
                .orElseThrow(() -> new AuthenticationFailedException(
                        "Invalid username/email or password. Please check your credentials and try again."));

        // Check if account is locked
        if (!user.isAccountNonLocked()) {
            log.warn("Login attempt for locked account: {}", user.getUsername());
            throw new AuthenticationFailedException(
                    "Account is temporarily locked due to multiple failed login attempts. Please try again later.");
        }

        // Check if account is active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            log.warn("Login attempt for inactive account: {}", user.getUsername());
            throw new AuthenticationFailedException("Account is deactivated. Please contact support.");
        }

        try {
            // Authenticate using Spring Security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsernameOrEmail(),
                            request.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Reset failed login attempts on successful login
            if (user.getFailedLoginAttempts() > 0) {
                user.resetFailedLoginAttempts();
            }
            
            // Update last login time
            user.updateLastLogin();
            userRepository.save(user);

            log.info("Successful login for user: {}", user.getUsername());
            return generateTokenResponse(user);

        } catch (BadCredentialsException e) {
            // Handle failed login attempt
            handleFailedLoginAttempt(user);
            log.warn("Failed login attempt for user: {}", user.getUsername());
            throw new AuthenticationFailedException(
                    "Invalid username/email or password. Please check your credentials and try again.");
        }
    }

    /**
     * Refresh access token using refresh token
     */
    public JwtAuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Attempting token refresh");

        String refreshToken = request.getRefreshToken();

        // Validate refresh token
        if (!tokenProvider.validateRefreshToken(refreshToken)) {
            throw new AuthenticationFailedException("Invalid or expired refresh token");
        }

        // Extract username from refresh token
        String username = tokenProvider.getUsernameFromToken(refreshToken);
        if (username == null) {
            throw new AuthenticationFailedException("Invalid refresh token");
        }

        // Find user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationFailedException("User not found"));

        // Check if user is still active
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AuthenticationFailedException("Account is deactivated");
        }

        log.info("Successfully refreshed token for user: {}", user.getUsername());
        return generateTokenResponse(user);
    }

    /**
     * Validate token and return user details
     */
    @Transactional(readOnly = true)
    public Map<String, Object> validateToken(String token) {
        log.debug("Validating token");

        if (!tokenProvider.validateAccessToken(token)) {
            return Map.of(
                "valid", false,
                "message", "Invalid or expired token"
            );
        }

        // Get user details from token
        Map<String, Object> userDetails = tokenProvider.getUserDetailsFromToken(token);
        
        return Map.of(
            "valid", true,
            "userDetails", userDetails
        );
    }

    /**
     * Initiate forgot password process
     */
    public void forgotPassword(ForgotPasswordRequest request) {
        log.info("Forgot password request for username: {}", request.getUsername());

        // Find user
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", request.getUsername()));

        // Check rate limiting for OTP requests
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long recentOtpCount = otpRepository.countOtpsCreatedByUsernameSince(user.getUsername(), oneHourAgo);
        
        if (recentOtpCount >= MAX_OTP_REQUESTS_PER_HOUR) {
            throw new InvalidInputException("Too many OTP requests. Please try again later.");
        }

        // Invalidate any existing OTPs for this user
        otpRepository.markAllOtpsAsUsedByUsername(user.getUsername());

        // Generate new OTP
        String otpCode = generateOTP();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES);

        Otp otp = Otp.builder()
                .username(user.getUsername())
                .otpCode(otpCode)
                .expiresAt(expiresAt)
                .isUsed(false)
                .build();

        otpRepository.save(otp);

        // In a real application, you would send the OTP via email/SMS
        // For now, we'll just log it (REMOVE THIS IN PRODUCTION)
        log.info("Generated OTP for user {}: {} (expires at: {})", 
                user.getUsername(), otpCode, expiresAt);

        log.info("OTP generated successfully for user: {}", user.getUsername());
    }

    /**
     * Reset password using OTP
     */
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Password reset request for username: {}", request.getUsername());

        // Find user
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", request.getUsername()));

        // Find and validate OTP
        Otp otp = otpRepository.findValidOtpByUsernameAndCode(request.getUsername(), request.getOtp())
                .orElseThrow(() -> new InvalidInputException("Invalid or expired OTP. Please request a new one."));

        try {
            // Update password
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            
            // Reset failed login attempts and unlock account
            user.resetFailedLoginAttempts();
            
            // Mark OTP as used
            otp.markAsUsed();
            
            // Save changes
            userRepository.save(user);
            otpRepository.save(otp);

            log.info("Password reset successfully for user: {}", user.getUsername());

        } catch (Exception e) {
            log.error("Error resetting password for user: {}", request.getUsername(), e);
            throw new RuntimeException("Failed to reset password", e);
        }
    }

    /**
     * Get current authenticated user
     */
    @Transactional(readOnly = true)
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationFailedException("No authenticated user found");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    /**
     * Check if current user has specific role
     */
    @Transactional(readOnly = true)
    public boolean hasRole(Role role) {
        try {
            User currentUser = getCurrentUser();
            return currentUser.hasRole(role);
        } catch (Exception e) {
            return false;
        }
    }

    // Private helper methods

    private JwtAuthResponse generateTokenResponse(User user) {
        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);
        
        long accessTokenExpiration = tokenProvider.getAccessTokenExpiration() / 1000; // Convert to seconds
        long refreshTokenExpiration = tokenProvider.getRefreshTokenExpiration() / 1000; // Convert to seconds
        
        LocalDateTime accessExpiresAt = LocalDateTime.now().plusSeconds(accessTokenExpiration);
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusSeconds(refreshTokenExpiration);

        return JwtAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .location(user.getLocation())
                .expiresAt(accessExpiresAt)
                .refreshExpiresAt(refreshExpiresAt)
                .expiresIn(accessTokenExpiration)
                .refreshExpiresIn(refreshTokenExpiration)
                .build();
    }

    private void handleFailedLoginAttempt(User user) {
        user.incrementFailedLoginAttempts();

        if (user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
            user.lockAccount(ACCOUNT_LOCK_DURATION_MINUTES);
            log.warn("Account locked for user: {} due to {} failed attempts", 
                    user.getUsername(), user.getFailedLoginAttempts());
        }

        userRepository.save(user);
    }

    private String generateOTP() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000); // Generate 6-digit OTP
        return String.valueOf(otp);
    }
}
