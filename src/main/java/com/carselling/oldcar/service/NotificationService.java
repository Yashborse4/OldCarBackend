package com.carselling.oldcar.service;

import com.carselling.oldcar.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to handle sending notifications (Email, Push, etc.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final EmailService emailService;
    private final com.carselling.oldcar.repository.UserRepository userRepository;

    public void registerDevice(Long userId, String deviceToken) {
        log.info("Registering device token for user: {}", userId);
        userRepository.findById(userId).ifPresent(user -> {
            user.setFcmToken(deviceToken);
            userRepository.save(user);
        });
    }

    public void unregisterDevice(Long userId, String deviceToken) {
        log.info("Unregistering device token for user: {}", userId);
        userRepository.findById(userId).ifPresent(user -> {
            // Only remove if it matches, to prevent removing a newer token from another
            // login
            if (deviceToken != null && deviceToken.equals(user.getFcmToken())) {
                user.setFcmToken(null);
                userRepository.save(user);
            } else if (deviceToken == null) {
                // If no token provided, force clear? generic unregister?
                // Safest is to only clear if matches.
                // But for now, let's assume if token is null we might not want to clear
                // anything or clear all?
                // Let's stick to safe removal.
            }
        });
    }

    public void sendDealerUpgradeNotification(User user) {
        log.info("Sending Dealer Upgrade Notification to user: {}", user.getEmail());
        String subject = "Welcome to Car World Dealer Program";
        String content = String.format("""
                <p>Congratulations <strong>%s</strong>!</p>
                <p>Your account has been upgraded to a specific <strong>Dealer Account</strong>.</p>
                <p>You can now list unlimited cars and access premium features.</p>
                <p>Please note that your dealer status is currently <strong>Pending Verification</strong>.
                Our team will review your details shortly.</p>
                """, user.getDisplayName());

        sendEmail(user.getEmail(), subject, content, "Go to Dashboard");
    }

    public void sendBatchJobCompletionNotification(User user, String jobType, String status, String details) {
        log.info("Sending Batch Job Notification to user: {}", user.getEmail());
        String subject = "Batch Job " + status + ": " + jobType;
        String content = String.format("""
                <p>Hello <strong>%s</strong>,</p>
                <p>Your batch job <strong>%s</strong> has <strong>%s</strong>.</p>
                <p>%s</p>
                """, user.getDisplayName(), jobType, status, details);

        sendEmail(user.getEmail(), subject, content, "View Dashboard");
    }

    public void sendDealerVerificationNotification(User user, boolean verified) {
        if (verified) {
            log.info("Sending Dealer Verification Approved Notification to user: {}", user.getEmail());
            String subject = "Dealer Verification Approved - Car World";
            String content = String.format("""
                    <p>Great news <strong>%s</strong>!</p>
                    <p>Your dealer profile has been <strong>VERIFIED</strong>.</p>
                    <p>Your listings will now carry the 'Verified Dealer' badge, increasing trust with buyers.</p>
                    """, user.getDisplayName());

            sendEmail(user.getEmail(), subject, content, "View Your Listings");
        } else {
            log.info("Sending Dealer Verification Revoked Notification to user: {}", user.getEmail());
            String subject = "Important: Dealer Verification Status Update";
            String content = String.format("""
                    <p>Hello <strong>%s</strong>,</p>
                    <p>Your dealer verification status has been <strong>REVOKED</strong>.</p>
                    <p>This may be due to incomplete documentation or policy violations.</p>
                    <p>Please contact support for more information.</p>
                    """, user.getDisplayName());

            sendEmail(user.getEmail(), subject, content, "Contact Support");
        }
    }

    private void sendEmail(String to, String subject, String bodyContent, String buttonText) {
        String htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { margin: 0; padding: 0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f4f4; }
                        .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); margin-top: 40px; margin-bottom: 40px; }
                        .header { background: linear-gradient(135deg, #1a73e8 0%, #0d47a1 100%); padding: 30px; text-align: center; color: white; }
                        .header h1 { margin: 0; font-size: 24px; font-weight: 600; letter-spacing: 1px; }
                        .content { padding: 40px; text-align: left; color: #333333; font-size: 16px; line-height: 1.6; }
                        .footer { background-color: #f8f9fa; padding: 20px; text-align: center; font-size: 12px; color: #888888; border-top: 1px solid #eeeeee; }
                        .button { display: inline-block; padding: 12px 24px; background-color: #0d47a1; color: white; text-decoration: none; border-radius: 6px; font-weight: 600; margin-top: 20px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Car World</h1>
                        </div>
                        <div class="content">
                            %s
                            <br>
                            <div style="text-align: center;">
                                <a href="#" class="button" style="color: white !important;">%s</a>
                            </div>
                        </div>
                        <div class="footer">
                            <p>&copy; %d Car World. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(bodyContent, buttonText, java.time.Year.now().getValue());

        emailService.sendHtmlEmail(to, subject, htmlContent);
    }
}
