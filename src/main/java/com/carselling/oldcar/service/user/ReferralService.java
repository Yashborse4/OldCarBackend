package com.carselling.oldcar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralService {

    @Value("${app.security.referral-secret:default-insecure-secret-change-me}")
    private String secretKey;

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long DEFAULT_EXPIRY_SECONDS = 7 * 24 * 60 * 60; // 7 days

    /**
     * Generates a signed referral token containing referrerId, carId, and expiry.
     */
    public String generateReferralToken(Long carId, Long referrerId) {
        long expiry = Instant.now().getEpochSecond() + DEFAULT_EXPIRY_SECONDS;
        String data = carId + ":" + referrerId + ":" + expiry;
        String signature = sign(data);

        // Return structured token: referrerId:expiry:signature
        // We don't need to put carId in the token if we validate against the visited
        // carId
        return referrerId + ":" + expiry + ":" + signature;
    }

    /**
     * Validates a referral token against the visited carId.
     * Returns the valid referrerId if successful, or null if invalid.
     */
    public Long validateReferral(Long visitedCarId, String token, Long currentUserId, Long carOwnerId) {
        if (token == null || token.isBlank()) {
            return null;
        }

        try {
            String[] parts = token.split(":");
            if (parts.length != 3) {
                log.warn("Invalid referral token format: {}", token);
                return null;
            }

            Long referrerId = Long.parseLong(parts[0]);
            long expiry = Long.parseLong(parts[1]);
            String signature = parts[2];

            // 1. Check Expiry
            if (Instant.now().getEpochSecond() > expiry) {
                log.warn("Referral token expired. Expiry: {}", expiry);
                return null;
            }

            // 2. Validate Signature
            // Reconstruct data: carId:referrerId:expiry
            String data = visitedCarId + ":" + referrerId + ":" + expiry;
            String expectedSignature = sign(data);

            if (!expectedSignature.equals(signature)) {
                log.warn("Invalid referral signature for car {}. Token malicious?", visitedCarId);
                return null;
            }

            // 3. Abuse Prevention Checks
            if (referrerId.equals(currentUserId)) {
                log.info("Self-referral detected. User {} referred themselves to car {}", currentUserId, visitedCarId);
                return null; // Block self-referral
            }

            if (referrerId.equals(carOwnerId)) {
                log.info("Owner-referral detected. Owner {} referred their own car {}", carOwnerId, visitedCarId);
                return null; // Block owner-referral
            }

            return referrerId;

        } catch (NumberFormatException e) {
            log.error("Error parsing referral token", e);
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to sign referral data", e);
        }
    }
}
