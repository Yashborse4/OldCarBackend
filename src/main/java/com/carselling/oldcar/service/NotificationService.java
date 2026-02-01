package com.carselling.oldcar.service;

import com.carselling.oldcar.event.NotificationEvent;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.UserDeviceToken;
import com.carselling.oldcar.repository.UserDeviceTokenRepository;
import com.carselling.oldcar.repository.UserRepository;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service to handle sending notifications (Email, Push, etc.)
 * Uses ApplicationEventPublisher to publish events for async processing.
 * Also handles Direct FCM Push Notifications.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;
    private final UserDeviceTokenRepository tokenRepository;

    /**
     * Register a device token for Push Notifications (Multi-device support)
     */
    @Transactional
    public void registerToken(Long userId, String token, String platformStr) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Normalize platform
        UserDeviceToken.Platform platform;
        try {
            platform = UserDeviceToken.Platform.valueOf(platformStr.toUpperCase());
        } catch (Exception e) {
            platform = UserDeviceToken.Platform.ANDROID; // Default
        }

        // Check if token exists
        Optional<UserDeviceToken> existingToken = tokenRepository.findByToken(token);

        if (existingToken.isPresent()) {
            UserDeviceToken t = existingToken.get();
            // If user changed, update owner
            if (!t.getUser().getId().equals(userId)) {
                t.setUser(user);
            }
            t.setPlatform(platform);
            t.setUpdatedAt(LocalDateTime.now());
            tokenRepository.save(t);
        } else {
            UserDeviceToken newToken = UserDeviceToken.builder()
                    .user(user)
                    .token(token)
                    .platform(platform)
                    .build();
            tokenRepository.save(newToken);
        }

        // Backwards compatibility: Update User entity's single token field just in case
        user.setFcmToken(token);
        userRepository.save(user); // Optimization: Could be removed if single token isn't used

        log.info("Registered FCM token for user {}", userId);
    }

    /**
     * Legacy method: Register single device (Delegates to new method)
     */
    public void registerDevice(Long userId, String deviceToken) {
        registerToken(userId, deviceToken, "ANDROID");
    }

    /**
     * Unregister device token
     */
    @Transactional
    public void unregisterToken(String token) {
        tokenRepository.findByToken(token).ifPresent(tokenRepository::delete);
        log.info("Unregistered FCM token");
    }

    /**
     * Legacy method: Unregister single device
     */
    public void unregisterDevice(Long userId, String deviceToken) {
        unregisterToken(deviceToken);
        // Clean up legacy field
        userRepository.findById(userId).ifPresent(user -> {
            if (deviceToken != null && deviceToken.equals(user.getFcmToken())) {
                user.setFcmToken(null);
                userRepository.save(user);
            }
        });
    }

    /**
     * Send Push Notification to a User (All their devices)
     */
    @Async
    public void sendToUser(Long userId, String title, String body, Map<String, String> data) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return;

        List<UserDeviceToken> tokens = tokenRepository.findByUser(user);
        if (tokens.isEmpty()) {
            // Fallback to legacy field
            if (user.getFcmToken() != null && !user.getFcmToken().isEmpty()) {
                sendToToken(user.getFcmToken(), title, body, data);
            }
            log.debug("No devices found for user {}", userId);
            return;
        }

        List<String> validTokens = tokens.stream()
                .map(UserDeviceToken::getToken)
                .collect(Collectors.toList());

        if (validTokens.isEmpty())
            return;

        MulticastMessage message = MulticastMessage.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data != null ? data : Map.of())
                .addAllTokens(validTokens)
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            log.info("Sent notification to user {}: {} successes, {} failures",
                    userId, response.getSuccessCount(), response.getFailureCount());

            // Clean up invalid tokens
            if (response.getFailureCount() > 0) {
                cleanupInvalidTokens(tokens, response.getResponses());
            }
        } catch (Exception e) {
            log.error("Failed to send notification to user {}", userId, e);
        }
    }

    /**
     * Helper: Send to single token (fallback)
     */
    private void sendToToken(String token, String title, String body, Map<String, String> data) {
        Message message = Message.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data != null ? data : Map.of())
                .setToken(token)
                .build();
        try {
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            log.error("Failed to send to legacy token", e);
        }
    }

    /**
     * Send Broadcast Notification to All Users
     */
    @Async
    public void sendToAll(String title, String body) {
        // Warning: Sending to topic is better for "All Users"
        Message message = Message.builder()
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setTopic("all_users")
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Sent broadcast notification: {}", response);
        } catch (Exception e) {
            log.error("Failed to send broadcast", e);
        }
    }

    private void cleanupInvalidTokens(List<UserDeviceToken> tokens, List<SendResponse> responses) {
        for (int i = 0; i < responses.size(); i++) {
            if (!responses.get(i).isSuccessful()) {
                // Check error code
                MessagingErrorCode errorCode = responses.get(i).getException().getMessagingErrorCode();
                if (errorCode == MessagingErrorCode.UNREGISTERED ||
                        errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                    // Token invalid
                    if (i < tokens.size()) {
                        tokenRepository.delete(tokens.get(i));
                    }
                }
            }
        }
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

        // Also send Push
        sendToUser(user.getId(), "Account Upgraded", "You are now a Dealer!", null);
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

        // Push
        sendToUser(user.getId(), "Batch Job " + status, jobType + " completed", null);
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

            sendToUser(user.getId(), "Dealer Verified", "Your profile is now verified!", null);
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

            sendToUser(user.getId(), "Verification Revoked", "Your dealer status has been revoked.", null);
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
