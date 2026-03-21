package com.carselling.oldcar.service.auth;

import com.carselling.oldcar.dto.auth.*;
import com.carselling.oldcar.exception.AuthenticationFailedException;
import com.carselling.oldcar.exception.InvalidInputException;
import com.carselling.oldcar.exception.ResourceAlreadyExistsException;
import com.carselling.oldcar.exception.ResourceNotFoundException;

import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.DealerStatus;
import com.carselling.oldcar.model.RefreshToken;
import com.carselling.oldcar.model.AuthProvider;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.repository.RefreshTokenRepository;
import com.carselling.oldcar.security.jwt.JwtTokenProvider;
import com.carselling.oldcar.util.SecurityUtils;
import com.carselling.oldcar.model.OtpPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.security.SecureRandom;
import com.carselling.oldcar.exception.AuthenticationFailedException.AuthError;

/**
 * Authentication Service - Aligned with API Requirements
 * Handles user authentication with device tracking and enhanced features
 */
@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpService otpService;
    private final GoogleAuthService googleAuthService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CacheManager cacheManager;

    public AuthServiceImpl(UserRepository userRepository, 
                           RefreshTokenRepository refreshTokenRepository, 
                           OtpService otpService, 
                           GoogleAuthService googleAuthService,
                           PasswordEncoder passwordEncoder, 
                           JwtTokenProvider jwtTokenProvider,
                           CacheManager cacheManager) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.otpService = otpService;
        this.googleAuthService = googleAuthService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.cacheManager = cacheManager;
    }

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

            // Check if username already exists, if so append random 3-digit number
            username = generateUniqueUsername(username);

            log.info("No username provided, extracting from email: {} -> {}", email, username);
            request.setUsername(username); // Update request object
        }
        log.info("Registering new user: {}", username);

        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResourceAlreadyExistsException("Username already exists");
        }

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceAlreadyExistsException("Email already registered");
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
                .authProvider(AuthProvider.LOCAL)
                .dealerStatus(role == Role.DEALER ? DealerStatus.UNVERIFIED : null)
                .isActive(true)
                .isEmailVerified(false) // In production, send verification email
                .isAccountNonLocked(true)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        user = userRepository.save(user);

        // Evict cache to ensure clean state (though unlikely to be cached yet)
        evictUserCache(user);

        // Send verification OTP once
        otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.EMAIL_VERIFICATION);

        long jwtExpirationSeconds = jwtTokenProvider.getJwtExpirationMs() / 1000;
        long refreshExpirationSeconds = jwtTokenProvider.getRefreshTokenExpirationMs() / 1000;

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
                .dealerStatus(user.getDealerStatus() != null ? user.getDealerStatus().name() : null)
                .dealerStatusDisplayName(user.getDealerStatus() != null ? user.getDealerStatus().getDisplayName() : null)
                .dealerStatusReason(user.getDealerStatusReason())
                .onboardingCompleted(user.getOnboardingCompleted() != null ? user.getOnboardingCompleted() : false)
                .expiresAt(null)
                .refreshExpiresAt(null)
                .expiresIn(jwtExpirationSeconds)
                .refreshExpiresIn(refreshExpirationSeconds)
                .build();
    }

    /**
     * Generate a unique username by appending random 3-digit number if needed
     * Example: yashborse -> yashborse123 (if yashborse is taken)
     * 
     * @param baseUsername The base username extracted from email
     * @return A unique username that doesn't exist in the database
     */
    private String generateUniqueUsername(String baseUsername) {
        String username = baseUsername;

        // If username is available, return it as-is
        if (!userRepository.existsByUsername(username)) {
            return username;
        }

        // Username is taken, try appending random 3-digit numbers
        int attempts = 0;
        int maxAttempts = 10; // Prevent infinite loop

        while (attempts < maxAttempts) {
            // Generate random 3-digit number (100-999)
            int randomNum = 100 + SECURE_RANDOM.nextInt(900);
            String candidateUsername = baseUsername + randomNum;

            if (!userRepository.existsByUsername(candidateUsername)) {
                log.info("Username '{}' is taken, using '{}' instead", baseUsername, candidateUsername);
                return candidateUsername;
            }

            attempts++;
        }

        // If all attempts failed (very unlikely), use timestamp-based suffix
        String timestampUsername = baseUsername + System.currentTimeMillis() % 1000;
        log.info("Username '{}' is taken, using timestamp-based '{}' instead", baseUsername, timestampUsername);
        return timestampUsername;
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
    @Transactional
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
                .orElseThrow(() -> new AuthenticationFailedException("User not found", AuthError.USER_NOT_FOUND));

        if (!user.isAccountNonLocked()) {
            throw new AuthenticationFailedException("Account is locked due to multiple failed login attempts",
                    AuthError.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleFailedLogin(user);
            throw new AuthenticationFailedException("Invalid credentials", AuthError.INVALID_CREDENTIALS);
        }

        if (Boolean.FALSE.equals(user.getIsEmailVerified())) {
            otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.EMAIL_VERIFICATION);
            log.info("Email not verified for user: {}. Sent verification OTP.", user.getUsername());

            Map<String, Object> data = new HashMap<>();
            data.put("redirectTo", "EMAIL_VERIFICATION");
            throw new AuthenticationFailedException(
                    "Email is not verified. A verification code has been sent to your email.",
                    AuthError.EMAIL_NOT_VERIFIED, data);
        }

        // Update last login time and device info
        updateLoginStats(user, request.getDeviceInfo());
        
        // Evict cache after updating stats
        evictUserCache(user);

        // Generate persistent tokens
        return generateAuthResponse(user);
    }

    /**
     * Login with Google
     */
    @Override
    @Transactional
    public JwtAuthResponse loginWithGoogle(GoogleLoginRequest request) {
        if (request == null || request.getIdToken() == null) {
            throw new InvalidInputException("Google login request or token cannot be null");
        }

        com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload payload = 
            googleAuthService.verifyToken(request.getIdToken());

        if (payload == null) {
            throw new AuthenticationFailedException("Invalid Google ID Token", AuthError.TOKEN_INVALID);
        }

        String email = payload.getEmail();
        String googleId = payload.getSubject();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");
        String givenName = (String) payload.get("given_name");
        String familyName = (String) payload.get("family_name");
        String phoneNumber = (String) payload.get("phone_number");
        Boolean emailVerified = payload.getEmailVerified();

        if (Boolean.FALSE.equals(emailVerified)) {
            log.warn("Google login rejected: Email not verified for {}", email);
            throw new AuthenticationFailedException("Google email not verified", AuthError.EMAIL_NOT_VERIFIED);
        }

        log.info("Google login attempt for email: {}, name: {}, googleId: {}", email, name, googleId);

        // Check if user exists by googleId first (most secure)
        Optional<User> userByGoogleId = userRepository.findByGoogleId(googleId);
        User user;

        if (userByGoogleId.isPresent()) {
            user = userByGoogleId.get();
        } else {
            // Check by email
            Optional<User> userByEmail = userRepository.findByEmail(email);
            if (userByEmail.isPresent()) {
                user = userByEmail.get();
                user.setGoogleId(googleId); // Link googleId
            } else {
                log.info("Creating new user for Google email: {}", email);
                
                String baseUsername = SecurityUtils.extractUsernameFromEmail(email);
                String username = generateUniqueUsername(baseUsername);

                user = User.builder()
                        .username(username)
                        .email(email)
                        .googleId(googleId)
                        .firstName(givenName)
                        .lastName(familyName)
                        .phoneNumber(phoneNumber)
                        .password(passwordEncoder.encode(generateRandomPassword(16)))
                        .role(Role.USER)
                        .authProvider(AuthProvider.GOOGLE)
                        .isActive(true)
                        .isEmailVerified(true)
                        .emailVerifiedAt(LocalDateTime.now())
                        .isAccountNonLocked(true)
                        .failedLoginAttempts(0)
                        .profileImageUrl(pictureUrl)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .onboardingCompleted(true)
                        .build();
                
                user = userRepository.save(user);
            }
        }

        // Sync details for existing/found users
        boolean updated = false;
        if (user.getAuthProvider() != AuthProvider.GOOGLE) {
            user.setAuthProvider(AuthProvider.GOOGLE);
            updated = true;
        }
        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
            updated = true;
        }
        if (user.getFirstName() == null && givenName != null) {
            user.setFirstName(givenName);
            updated = true;
        }
        if (user.getLastName() == null && familyName != null) {
            user.setLastName(familyName);
            updated = true;
        }
        if (user.getProfileImageUrl() == null && pictureUrl != null) {
            user.setProfileImageUrl(pictureUrl);
            updated = true;
        }
        if (user.getPhoneNumber() == null && phoneNumber != null) {
            user.setPhoneNumber(phoneNumber);
            updated = true;
        }
        if (Boolean.FALSE.equals(user.getIsEmailVerified())) {
            user.setIsEmailVerified(true);
            user.setEmailVerifiedAt(LocalDateTime.now());
            updated = true;
        }

        if (updated) {
            user.setUpdatedAt(LocalDateTime.now());
            user = userRepository.save(user);
        }

        if (!user.isAccountNonLocked()) {
            throw new AuthenticationFailedException("Account is locked", AuthError.ACCOUNT_LOCKED);
        }

        // Update last login
        updateLoginStats(user, request.getDeviceInfo());
        evictUserCache(user);

        return generateAuthResponse(user);
    }

    /**
     * Request OTP for Email Verification
     */
    @Transactional
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
            throw new AuthenticationFailedException("Invalid or expired OTP", AuthError.INVALID_OTP);
        }

        // User status update is handled in OtpService.validateOtp ->
        // markEmailAsVerified
        // Fetch refreshed user to ensure we have updated state
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Generate tokens for auto-login
        JwtAuthResponse response = generateAuthResponse(user);
        
        // Evict cache as user's verification state changed
        evictUserCache(user);
        
        return response;
    }

    /**
     * Request OTP for Login
     */
    @Transactional
    public void requestLoginOtp(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationFailedException("User not found", AuthError.USER_NOT_FOUND));

        if (!user.isAccountNonLocked()) {
            throw new AuthenticationFailedException("Account is locked", AuthError.ACCOUNT_LOCKED);
        }

        if (!Boolean.TRUE.equals(user.getIsEmailVerified())) {
            throw new AuthenticationFailedException("Email is not verified", AuthError.EMAIL_NOT_VERIFIED);
        }

        otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.LOGIN);
    }

    /**
     * Login with OTP
     */
    @Transactional
    public JwtAuthResponse loginWithOtp(String email, String otp) {
        boolean isValid = otpService.validateOtp(email, otp, OtpPurpose.LOGIN.name());
        if (!isValid) {
            throw new AuthenticationFailedException("Invalid or expired OTP", AuthError.INVALID_OTP);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check account lock status again just in case
        if (!user.isAccountNonLocked()) {
            throw new AuthenticationFailedException("Account is locked", AuthError.ACCOUNT_LOCKED);
        }

        // Update stats
        updateLoginStats(user, null);

        // Sync auth provider if not set
        if (user.getAuthProvider() == AuthProvider.LOCAL) { // Default is LOCAL, change to PHONE
            user.setAuthProvider(AuthProvider.PHONE);
            userRepository.save(user);
        }
        
        // Evict cache
        evictUserCache(user);

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

    /**
     * Generate Access and persistent Refresh Token
     */
    @Transactional
    protected JwtAuthResponse generateAuthResponse(User user) {
        // Evict cache to ensure that any session-linked data in Redis is synchronized 
        // with the new token generation event.
        evictUserCache(user);

        long jwtExpirationMs = jwtTokenProvider.getJwtExpirationMs();
        long refreshExpirationMs = jwtTokenProvider.getRefreshTokenExpirationMs();
        long jwtExpirationSeconds = jwtExpirationMs / 1000;
        long refreshExpirationSeconds = refreshExpirationMs / 1000;

        // 1. Generate Access Token (Using configured duration)
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        // 2. Generate Refresh Token (Using configured duration)
        String refreshTokenString = jwtTokenProvider.generateRefreshToken(user);

        // 3. Persist Refresh Token
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusNanos(refreshExpirationMs * 1000000L);
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshTokenString)
                .expiryDate(refreshExpiresAt)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshTokenEntity);

        LocalDateTime expiresAt = LocalDateTime.now().plusNanos(jwtExpirationMs * 1000000L);

        return JwtAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenString)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .location(user.getLocation())
                .address(user.getAddress())
                .showroomName(user.getShowroomName())
                .phoneNumber(user.getPhoneNumber())
                .latitude(user.getLatitude())
                .longitude(user.getLongitude())
                .profileImageUrl(user.getProfileImageUrl())
                .emailVerified(Boolean.TRUE.equals(user.getIsEmailVerified()))
                .verifiedDealer(user.getDealerStatus() == DealerStatus.VERIFIED)
                .dealerStatus(user.getDealerStatus() != null ? user.getDealerStatus().name() : null)
                .dealerStatusDisplayName(user.getDealerStatus() != null ? user.getDealerStatus().getDisplayName() : null)
                .dealerStatusReason(user.getDealerStatusReason())
                .onboardingCompleted(user.getOnboardingCompleted() != null ? user.getOnboardingCompleted() : false)
                .verificationReminder(
                        (user.getRole() == Role.DEALER
                                && (user.getDealerStatus() == null
                                        || user.getDealerStatus() != DealerStatus.VERIFIED))
                                                ? "Reminder: Your dealer verification is pending. Please complete verification to list cars publicly and gain buyer trust."
                                                : null)
                .expiresAt(expiresAt)
                .refreshExpiresAt(refreshExpiresAt)
                .expiresIn(jwtExpirationSeconds)
                .refreshExpiresIn(refreshExpirationSeconds)
                .build();
    }

    /**
     * Refresh JWT token with Database Persistence Check
     */
    @Transactional
    public JwtAuthResponse refreshToken(RefreshTokenRequest request) {
        if (request == null) {
            throw new InvalidInputException("Refresh token request cannot be null");
        }

        String incomingToken = request.getRefreshToken();
        if (incomingToken == null || incomingToken.trim().isEmpty()) {
            throw new InvalidInputException("Refresh token is required");
        }

        log.info("Token refresh request");

        // 1. Validate against DB
        Optional<RefreshToken> tokenOptional = refreshTokenRepository.findByToken(incomingToken);
        if (tokenOptional.isEmpty()) {
            log.warn("Refresh token refresh failed: Token not found in database. Token hash: {}...", 
                     incomingToken.substring(0, Math.min(10, incomingToken.length())));
            throw new AuthenticationFailedException("Refresh token not found in database", AuthError.TOKEN_INVALID);
        }
        RefreshToken storedToken = tokenOptional.get();

        // 2. Check revocation with Grace Period (30 seconds)
        if (storedToken.isRevoked()) {
            LocalDateTime revokedAt = storedToken.getRevokedAt();
            if (revokedAt != null && revokedAt.isAfter(LocalDateTime.now().minusSeconds(30))) {
                log.info("Concurrent refresh request: allowing recently rotated token for user: {}", 
                         storedToken.getUser().getUsername());
            } else {
                // Security Alert: Attempt to use revoked token outside grace period.
                refreshTokenRepository.deleteByUser(storedToken.getUser());
                throw new AuthenticationFailedException("Refresh token was revoked (outside grace period)", AuthError.TOKEN_INVALID);
            }
        }

        // 3. Check Expiry
        if (storedToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            log.warn("Refresh token expired for user: {}. Expiry: {}", storedToken.getUser().getUsername(), storedToken.getExpiryDate());
            refreshTokenRepository.delete(storedToken); // cleanup
            throw new AuthenticationFailedException("Refresh token expired", AuthError.TOKEN_INVALID);
        }

        // 4. Validate JWT structure/signature
        if (!jwtTokenProvider.validateToken(incomingToken)) {
            throw new AuthenticationFailedException("Invalid refresh token signature", AuthError.TOKEN_INVALID);
        }

        // 5. Rotate Token (Security Best Practice with Soft Revocation)
        // Instead of immediate delete, we mark as revoked to support concurrency
        if (!storedToken.isRevoked()) {
            storedToken.setRevoked(true);
            storedToken.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(storedToken);
        }

        // Generate new Pair
        User user = storedToken.getUser();
        
        // Force cache eviction before returning new tokens, ensuring any subsequent
        // requests based on the user principal will reload fresh data from DB
        // rather than using potentially corrupted cached entry.
        evictUserCache(user);
        
        return generateAuthResponse(user);
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
                    .dealerStatus(user.getDealerStatus() != null ? user.getDealerStatus().name() : null)
                    .dealerStatusDisplayName(user.getDealerStatus() != null ? user.getDealerStatus().getDisplayName() : null)
                    .dealerStatusReason(user.getDealerStatusReason())
                    .onboardingCompleted(Boolean.TRUE.equals(user.getOnboardingCompleted()))
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
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Password reset request for user: {}", request.getUsername());

        // Find user by username or email to allow password reset via either
        Optional<User> userOpt = userRepository.findByUsername(request.getUsername());
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(request.getUsername());
        }

        if (userOpt.isEmpty()) {
            // Don't reveal if user exists - throw generic exception
            throw new AuthenticationFailedException("Invalid credentials", AuthError.INVALID_CREDENTIALS);
        }

        User user = userOpt.get();

        // Validate OTP using OtpService
        boolean isValid = otpService.validateOtp(user.getEmail(), request.getOtp(), "PASSWORD_RESET");

        if (!isValid) {
            throw new AuthenticationFailedException("Invalid or expired OTP", AuthError.INVALID_OTP);
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());

        // Revoke all existing sessions on password reset
        refreshTokenRepository.deleteByUser(user);

        // Reset account lock if any
        user.setAccountNonLocked(true);
        user.setFailedLoginAttempts(0);
        user.setAccountLockedAt(null);

        userRepository.save(user);

        // Evict cache after password reset or lock change
        evictUserCache(user);

        log.info("Password reset successful for user: {}", request.getUsername());
    }

    /**
     * Logout (Revoke DB Token)
     */
    @Transactional
    public void logout(String token) {
        log.info("Logout request");
        if (token == null || token.trim().isEmpty()) {
            log.warn("Logout requested without a valid token");
            return;
        }

        // If 'token' is access token, we might not find it in DB if we only store
        // Refresh Tokens.
        // Usually, logout sends Refresh Token to be revoked.
        // If client sends Access Token, we can try to blacklist it in memory (short
        // term)
        // AND ideally find associated refresh tokens if linked.

        // For this implementation, we assume we receive the Refresh Token for logout,
        // or we just rely on access token expiry.
        // But the controller typically extracts "Bearer <accessToken>".
        // If we want to revoke based on Access Token, we need to blacklist it.
        jwtTokenProvider.blacklistToken(token);

        // If we want to revoke the Refresh Token, the client should send it.
        // Or we can revoke all tokens for the user if we can extract ID.
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        if (userId != null) {
            // Optional: Revoke all for user? Or just let access token expire?
            // Checking requirements. "Reliable logout" implies invalidating session.
            // If we don't have the specific refresh token handle, we might be aggressive or
            // just wait.
        }
    }

    /**
     * Revoke a specific refresh token (to be called by controller if passed)
     */
    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    // or refreshTokenRepository.delete(token);
                });
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
        
        // Evict cache after failed login attempt/lock
        evictUserCache(user);
    }

    /**
     * Get current authenticated user
     */
    public User getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new AuthenticationFailedException("No authenticated user found", AuthError.AUTH_FAILED);
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

    /**
     * Evict user from all relevant caches to ensure Redis consistency
     */
    private void evictUserCache(User user) {
        if (user == null) return;
        
        try {
            log.debug("Evicting user {} from caches", user.getUsername());
            
            // Evict from users_v4 (keyed by username/email)
            Cache userCache = cacheManager.getCache("users_v4");
            if (userCache != null) {
                userCache.evict(user.getEmail());
                userCache.evict(user.getUsername());
            }
            
            // Evict from usersById_v4 (keyed by ID)
            Cache userByIdCache = cacheManager.getCache("usersById_v4");
            if (userByIdCache != null && user.getId() != null) {
                userByIdCache.evict(user.getId());
            }
            
            log.debug("Successfully evicted user {} from caches", user.getUsername());
        } catch (Exception e) {
            log.error("Failed to evict user {} from cache: {}", user.getUsername(), e.getMessage());
        }
    }

}
