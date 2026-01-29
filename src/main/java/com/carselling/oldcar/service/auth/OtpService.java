package com.carselling.oldcar.service.auth;

import com.carselling.oldcar.service.EmailService;

import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.Otp;
import com.carselling.oldcar.model.OtpPurpose;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.OtpRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for OTP generation, validation, and management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpRepository otpRepository;
    private final EmailService emailService;
    private final com.carselling.oldcar.service.MobileOtpService mobileOtpService;
    private final com.carselling.oldcar.repository.UserRepository userRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private static final int OTP_VALIDITY_MINUTES = 5;
    private static final int MAX_ATTEMPTS = 5;

    @org.springframework.beans.factory.annotation.Value("${app.otp.enabled:true}")
    private boolean otpEnabled;

    /**
     * Generate and send OTP for email verification
     */
    @Transactional
    public void generateAndSendOtp(String email, OtpPurpose purpose) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Invalidate previous OTPs
        // Assuming invalidatePreviousOtps expects String purpose, we convert enum to
        // string if needed by repository
        // But better to update repository if possible. For now, let's look at
        // repository.
        // wait, I can pass purpose.name()
        otpRepository.invalidatePreviousOtps(user.getEmail(), purpose.name());

        log.info("Generating OTP for email: {} purpose: {}", email, purpose);

        // 1. Check Cooldown (60 seconds)
        Optional<Otp> latestOtp = otpRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose.name());
        if (latestOtp.isPresent()) {
            long secondsSinceLast = java.time.Duration.between(latestOtp.get().getCreatedAt(), LocalDateTime.now())
                    .getSeconds();
            if (secondsSinceLast < 60) {
                throw new RuntimeException(
                        "Please wait " + (60 - secondsSinceLast) + " seconds before requesting a new OTP");
            }
        }

        // 2. Check Rate Limit (Max 5 per hour)
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        long count = otpRepository.countByEmailAndPurposeAndCreatedAtAfter(email, purpose.name(), oneHourAgo);
        if (count >= 5) {
            throw new RuntimeException("Too many OTP requests. Please try again after 1 hour.");
        }

        // 3. Generate plain 6-digit code
        String otpCode = String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextInt(100000, 999999));

        // 4. Hash the OTP for secure storage (CRITICAL SECURITY STEP)
        String otpHash = passwordEncoder.encode(otpCode);

        log.debug("Generated OTP hash for user: {}", email);

        // 5. Save to DB (With User association) - ONLY store the hash
        Otp otp = Otp.builder()
                .email(user.getEmail())
                .username(user.getUsername())
                .user(user)
                .otpHash(otpHash) // Store ONLY the hashed version
                // .otpCode(otpCode) // NEVER store plain OTP
                .purpose(purpose.name()) // Store as String in DB
                .expiresAt(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES))
                .attempts(0)
                .maxAttempts(MAX_ATTEMPTS)
                .used(false)
                .build();

        otpRepository.save(otp);

        // 6. Send Email (send plain OTP via email, but never store it)
        if (otpEnabled) {
            emailService.sendOtpEmail(email, otpCode, purpose.name());

            // 7. Send Mobile OTP (if enabled in MobileOtpService and user has phone)
            if (user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank()) {
                mobileOtpService.sendOk(user.getPhoneNumber(), otpCode, purpose.name());
            }

            log.info("OTP sent to {} (and phone if available)", email);
        } else {
            log.info("OTP delivery disabled. OTP for {}: {}", email, otpCode);
        }
    }

    /**
     * Validate an OTP request using secure hash comparison
     */
    @Transactional
    public boolean validateOtp(String email, String inputOtp, String purpose) {
        if (email == null || email.trim().isEmpty()) {
            log.warn("Email is required for OTP validation");
            return false;
        }

        if (inputOtp == null || inputOtp.trim().isEmpty()) {
            log.warn("OTP is required for validation");
            return false;
        }

        if (purpose == null || purpose.trim().isEmpty()) {
            log.warn("Purpose is required for OTP validation");
            return false;
        }

        // Find latest unused OTP
        Optional<Otp> otpOptional = otpRepository.findTopByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(email,
                purpose);

        if (otpOptional.isEmpty()) {
            log.warn("No active OTP found for {}", email);
            return false;
        }

        Otp otp = otpOptional.get();

        // Check if expired
        if (otp.isExpired()) {
            log.warn("OTP expired for {}", email);
            return false;
        }

        // Check if blocked (too many attempts)
        if (otp.isBlocked()) {
            log.warn("OTP blocked for {} due to too many attempts", email);
            return false;
        }

        // Trim input OTP to avoid whitespace issues
        inputOtp = inputOtp.trim();
        log.info("Validating OTP for {}: inputLength={}, inputMasked={}", email, inputOtp.length(),
                inputOtp.length() >= 2 ? inputOtp.substring(0, 1) + "****" + inputOtp.substring(inputOtp.length() - 1)
                        : "****");

        // Verify using BCrypt password matching (SECURE)
        if (passwordEncoder.matches(inputOtp, otp.getOtpHash())) {
            // Success
            otp.setUsed(true);
            otpRepository.save(otp);

            // Perform action based on purpose
            if ("EMAIL_VERIFICATION".equals(purpose)) {
                markEmailAsVerified(email);
            }

            log.info("OTP verified successfully for {}", email);
            return true;
        } else {
            // Failed attempt
            otp.incrementAttempts();
            otpRepository.save(otp);
            log.warn("Invalid OTP attempt for {} (Attempt {}/{}). Hash match failed.", email, otp.getAttempts(),
                    otp.getMaxAttempts());
            return false;
        }
    }

    private void markEmailAsVerified(String email) {
        if (email == null || email.trim().isEmpty()) {
            log.warn("Email cannot be null or empty for verification");
            return;
        }

        // Double check: ensure this is only called when purpose was explicitly
        // EMAIL_VERIFICATION
        // (This contract is enforced by validateOtp, but good to be defensive if moved)
        userRepository.findByEmail(email).ifPresent(user -> {
            if (Boolean.TRUE.equals(user.getIsEmailVerified())) {
                return; // Already verified
            }
            user.setIsEmailVerified(true);
            user.setEmailVerifiedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("User {} {} email marked as verified", user.getId(), email);
        });
    }

    /**
     * Cron job to cleanup expired OTPs
     * Runs every hour
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredOtps() {
        log.info("Starting expired OTP cleanup");
        try {
            // Retain OTPs for 24 hours to ensure rate limiting checks (which look back 1
            // hour) works correctly,
            // and provides some history for debugging if needed.
            LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
            otpRepository.deleteByExpiresAtBefore(cutoff);

            log.info("Expired OTP cleanup completed. Deleted OTPs older than {}", cutoff);
        } catch (Exception e) {
            log.error("Error during OTP cleanup", e);
        }
    }
}
