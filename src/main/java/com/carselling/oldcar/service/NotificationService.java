package com.carselling.oldcar.service;

import com.carselling.oldcar.event.NotificationEvent;
import com.carselling.oldcar.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service to handle sending notifications (Email, Push, etc.)
 * Uses ApplicationEventPublisher to publish events for async processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final ApplicationEventPublisher eventPublisher;
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
            if (deviceToken != null && deviceToken.equals(user.getFcmToken())) {
                user.setFcmToken(null);
                userRepository.save(user);
            }
        });
    }

    public void sendDealerUpgradeNotification(User user) {
        log.info("Publishing Dealer Upgrade Notification event for user: {}", user.getEmail());
        String subject = "Welcome to Car World Dealer Program";
        String content = String.format("""
                <p>Congratulations <strong>%s</strong>!</p>
                <p>Your account has been upgraded to a specific <strong>Dealer Account</strong>.</p>
                <p>You can now list unlimited cars and access premium features.</p>
                <p>Please note that your dealer status is currently <strong>Pending Verification</strong>.
                Our team will review your details shortly.</p>
                """, user.getDisplayName());

        publishNotification(user, NotificationEvent.NotificationType.DEALER_UPGRADE, subject, content,
                "Go to Dashboard", null);
    }

    public void sendBatchJobCompletionNotification(User user, String jobType, String status, String details) {
        log.info("Publishing Batch Job Notification event for user: {}", user.getEmail());
        String subject = "Batch Job " + status + ": " + jobType;
        String content = String.format("""
                <p>Hello <strong>%s</strong>,</p>
                <p>Your batch job <strong>%s</strong> has <strong>%s</strong>.</p>
                <p>%s</p>
                """, user.getDisplayName(), jobType, status, details);

        publishNotification(user, NotificationEvent.NotificationType.BATCH_JOB_COMPLETION, subject, content,
                "View Dashboard",
                Map.of("jobType", jobType, "status", status));
    }

    public void sendDealerVerificationNotification(User user, boolean verified) {
        if (verified) {
            log.info("Publishing Dealer Verification Approved event for user: {}", user.getEmail());
            String subject = "Dealer Verification Approved - Car World";
            String content = String.format("""
                    <p>Great news <strong>%s</strong>!</p>
                    <p>Your dealer profile has been <strong>VERIFIED</strong>.</p>
                    <p>Your listings will now carry the 'Verified Dealer' badge, increasing trust with buyers.</p>
                    """, user.getDisplayName());

            publishNotification(user, NotificationEvent.NotificationType.DEALER_VERIFICATION_APPROVED, subject, content,
                    "View Your Listings", null);
        } else {
            log.info("Publishing Dealer Verification Revoked event for user: {}", user.getEmail());
            String subject = "Important: Dealer Verification Status Update";
            String content = String.format("""
                    <p>Hello <strong>%s</strong>,</p>
                    <p>Your dealer verification status has been <strong>REVOKED</strong>.</p>
                    <p>This may be due to incomplete documentation or policy violations.</p>
                    <p>Please contact support for more information.</p>
                    """, user.getDisplayName());

            publishNotification(user, NotificationEvent.NotificationType.DEALER_VERIFICATION_REVOKED, subject, content,
                    "Contact Support", null);
        }
    }

    /**
     * Publishes a notification event for async processing.
     */
    private void publishNotification(User user, NotificationEvent.NotificationType type, String subject, String content,
            String actionUrl, Map<String, Object> metadata) {
        NotificationEvent event = new NotificationEvent(this, user, type, subject, content, actionUrl, metadata);
        eventPublisher.publishEvent(event);
        log.debug("Notification event published: Type={}, User={}", type, user.getEmail());
    }
}
