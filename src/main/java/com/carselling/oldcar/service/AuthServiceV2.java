package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.auth.*;
import com.carselling.oldcar.exception.AuthenticationFailedException;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.exception.ResourceAlreadyExistsException;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.Otp;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.repository.OtpRepository;
import com.carselling.oldcar.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

/**
 * Enhanced Authentication Service V2 - Aligned with API Requirements
 * Handles user authentication with device tracking and enhanced features
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AuthServiceV2 {

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Register a new user
     */
    public RegisterResponse registerUser(RegisterRequest request) {
        log.info("Registering new user: {}", request.getUsername());

        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResourceAlreadyExistsException("Username already exists: " + request.getUsername());
        }

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceAlreadyExistsException("Email already exists: " + request.getEmail());
        }

        // Create new user
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.valueOf(request.getRole().toUpperCase()))
                .isActive(true)
                .isEmailVerified(false) // In production, send verification email
                .isAccountNonLocked(true)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        user = userRepository.save(user);

        return RegisterResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * Login user with device info tracking
     */
    public JwtAuthResponseV2 loginUser(LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsernameOrEmail());

        // Find user by username or email
        User user = userRepository.findByUsernameOrEmail(request.getUsernameOrEmail(), request.getUsernameOrEmail())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid credentials"));

        // Check if account is locked
        if (!user.isAccountNonLocked()) {
            throw new AuthenticationFailedException("Account is locked due to multiple failed login attempts");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleFailedLogin(user);
            throw new AuthenticationFailedException("Invalid credentials");
        }

        // Reset failed login attempts on successful login
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setAccountLockedAt(null);
            userRepository.save(user);
        }

        // Update last login time and device info
        user.setLastLoginAt(LocalDateTime.now());
        if (request.getDeviceInfo() != null) {
            // Store device info - in production, you might want a separate DeviceSession table
            user.setLastLoginDevice(request.getDeviceInfo().getPlatform());
        }
        userRepository.save(user);

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(900); // 15 minutes
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusSeconds(604800); // 7 days

        return JwtAuthResponseV2.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .location(user.getLocation())
                .expiresAt(expiresAt)
                .refreshExpiresAt(refreshExpiresAt)
                .expiresIn(900L)
                .refreshExpiresIn(604800L)
                .build();
    }

    /**
     * Refresh token
     */
    public JwtAuthResponseV2 refreshToken(RefreshTokenRequest request) {
        log.info("Token refresh request");

        if (!jwtTokenProvider.validateToken(request.getRefreshToken())) {
            throw new AuthenticationFailedException("Invalid refresh token");
        }

        if (!jwtTokenProvider.isRefreshToken(request.getRefreshToken())) {
            throw new AuthenticationFailedException("Token is not a refresh token");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Generate new tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(900);
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusSeconds(604800);

        return JwtAuthResponseV2.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .location(user.getLocation())
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
        boolean isValid = jwtTokenProvider.validateToken(token);
        
        if (!isValid) {
            return TokenValidationResponse.builder()
                    .valid(false)
                    .build();
        }

        try {
            String userId = jwtTokenProvider.getUserIdFromToken(token);
            User user = userRepository.findById(Long.parseLong(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            TokenValidationResponse.UserDetails userDetails = TokenValidationResponse.UserDetails.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .location(user.getLocation())
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
    public void forgotPassword(ForgotPasswordRequestV2 request) {
        log.info("Forgot password request for username: {}", request.getUsername());

        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            log.warn("Forgot password request for non-existent username: {}", request.getUsername());
            return; // Don't reveal if username exists
        }

        User user = userOpt.get();

        // Generate OTP
        String otpValue = generateOtp();
        
        // Save OTP
        Otp otp = Otp.builder()
                .user(user)
                .otpValue(otpValue)
                .expiryTime(LocalDateTime.now().plusMinutes(5))
                .isUsed(false)
                .createdAt(LocalDateTime.now())
                .build();

        otpRepository.save(otp);

        // In production, send OTP via email/SMS
        log.info("OTP generated for user {}: {}", request.getUsername(), otpValue);
    }

    /**
     * Reset password with OTP
     */
    public void resetPassword(ResetPasswordRequestV2 request) {
        log.info("Password reset request for username: {}", request.getUsername());

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Find valid OTP
        Otp otp = otpRepository.findByUserAndOtpValueAndIsUsedFalse(user, request.getOtp())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid or expired OTP"));

        if (otp.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new AuthenticationFailedException("OTP has expired");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        
        // Reset account lock if any
        user.setAccountNonLocked(true);
        user.setFailedLoginAttempts(0);
        user.setAccountLockedAt(null);
        
        userRepository.save(user);

        // Mark OTP as used
        otp.setUsed(true);
        otpRepository.save(otp);

        log.info("Password reset successful for user: {}", request.getUsername());
    }

    /**
     * Logout (token blacklisting could be implemented here)
     */
    public void logout(String token) {
        log.info("Logout request");
        // In a stateless JWT system, logout is typically handled client-side
        // However, you could implement token blacklisting here if needed
    }

    /**
     * Check username availability
     */
    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    /**
     * Check email availability
     */
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
     * Generate 6-digit OTP
     */
    private String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
}
