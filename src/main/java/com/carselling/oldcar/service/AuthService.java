package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.auth.*;
import com.carselling.oldcar.exception.AuthenticationFailedException;
import com.carselling.oldcar.exception.InvalidInputException;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.exception.ResourceAlreadyExistsException;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.Otp;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.repository.OtpRepository;
import com.carselling.oldcar.security.jwt.JwtTokenProvider;
import com.carselling.oldcar.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Authentication Service - Aligned with API Requirements
 * Handles user authentication with device tracking and enhanced features
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OtpDeliveryService otpDeliveryService;

    /**
     * Register a new user
     */
    @Transactional
    public JwtAuthResponseV2 registerUser(RegisterRequest request) {
        if (request == null) {
            throw new InvalidInputException("Registration request cannot be null");
        }

        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new InvalidInputException("Email is required for registration");
        }

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new InvalidInputException("Password is required for registration");
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

        // Create new user
        // IMPORTANT: Frontend must NOT control role during registration.
        // Regardless of any incoming role hint, we always assign USER here.
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .isActive(true)
                .isEmailVerified(false) // In production, send verification email
                .verifiedDealer(false)
                .isAccountNonLocked(true)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        user = userRepository.save(user);

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
                .emailVerified(Boolean.TRUE.equals(user.getIsEmailVerified()))
                .verifiedDealer(Boolean.TRUE.equals(user.getVerifiedDealer()))
                .expiresAt(expiresAt)
                .refreshExpiresAt(refreshExpiresAt)
                .expiresIn(900L)
                .refreshExpiresIn(604800L)
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
            // Store device info - in production, you might want a separate DeviceSession
            // table
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
                .emailVerified(Boolean.TRUE.equals(user.getIsEmailVerified()))
                .verifiedDealer(Boolean.TRUE.equals(user.getVerifiedDealer()))
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

        Long userId = jwtTokenProvider.getUserIdFromToken(request.getRefreshToken());
        User user = userRepository.findById(userId)
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
                .emailVerified(Boolean.TRUE.equals(user.getIsEmailVerified()))
                .verifiedDealer(Boolean.TRUE.equals(user.getVerifiedDealer()))
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
                    .verifiedDealer(Boolean.TRUE.equals(user.getVerifiedDealer()))
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

        // Generate OTP
        String otpValue = generateOtp();

        // Save OTP
        Otp otp = Otp.builder()
                .user(user)
                .username(user.getUsername())
                .otpCode(otpValue)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .isUsed(false)
                .createdAt(LocalDateTime.now())
                .build();

        otpRepository.save(otp);

        otpDeliveryService.sendPasswordResetOtp(user, otpValue);
        log.info("OTP generated for user: {}", request.getUsername());
    }

    /**
     * Reset password with OTP
     */
    public void resetPassword(ResetPasswordRequestV2 request) {
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AuthenticationFailedException("No authenticated user found");
        }

        String userId = authentication.getName();
        return userRepository.findById(Long.parseLong(userId))
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
     * Generate 6-digit OTP using cryptographically secure random
     */
    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    /**
     * Parse role from string with null-safety and defaults to USER
     */
    private Role parseRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            log.info("No role provided, defaulting to USER");
            return Role.USER;
        }
        try {
            return Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role provided: {}, defaulting to USER", role);
            return Role.USER;
        }
    }
}
