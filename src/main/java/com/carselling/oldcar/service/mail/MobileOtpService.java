package com.carselling.oldcar.service.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for sending Mobile OTPs via SMS
 * Currently disabled by feature flag
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MobileOtpService {

    @Value("${app.sms.enabled:false}")
    private boolean isSmsEnabled;

    @Value("${app.sms.provider:mock}")
    private String smsProvider;

    @Async
    public void sendOk(String phoneNumber, String otpCode, String purpose) {
        if (!isSmsEnabled) {
            log.info("SMS service is disabled. Skipping OTP send to {}", phoneNumber);
            return;
        }

        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("Skipping SMS OTP send because phone number is blank");
            return;
        }

        try {
            log.info("Sending SMS OTP to: {} (Provider: {})", phoneNumber, smsProvider);

            // Mock Implementation for now
            // In future, integrate with Twilio, AWS SNS, or local SMS gateway
            if ("mock".equalsIgnoreCase(smsProvider)) {
                log.info("Mock SMS sent to {}: Your Car World code is {}", phoneNumber, otpCode);
            } else {
                // Mock SMS implementation
                log.info("Sending OTP {} to mobile number {}", otpCode, phoneNumber);

                // In production, integrate with Twilio/SNS/etc here
                // smsProvider.send(mobileNumber, "Your Verification Code is: " + otp);

                log.warn("SMS provider '{}' not implemented yet", smsProvider);
            }

        } catch (Exception e) {
            log.error("Failed to send SMS to {}", phoneNumber, e);
        }
    }
}
