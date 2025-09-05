package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.notification.NotificationRequest;
import com.carselling.oldcar.dto.notification.NotificationResponse;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.UserRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling push notifications via Firebase Cloud Messaging
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${firebase.service-account-key:}")
    private String firebaseServiceAccountKey;

    @Value("${firebase.project-id:car-selling-app}")
    private String firebaseProjectId;

    private FirebaseMessaging firebaseMessaging;

    @PostConstruct
    public void initialize() {
        try {
            if (!firebaseServiceAccountKey.isEmpty()) {
                FileInputStream serviceAccount = new FileInputStream(firebaseServiceAccountKey);
                
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setProjectId(firebaseProjectId)
                    .build();
                
                if (FirebaseApp.getApps().isEmpty()) {
                    FirebaseApp.initializeApp(options);
                }
                
                firebaseMessaging = FirebaseMessaging.getInstance();
                log.info("Firebase Cloud Messaging initialized successfully");
            } else {
                log.warn("Firebase service account key not provided. Push notifications will be disabled.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Firebase Cloud Messaging: {}", e.getMessage(), e);
        }
    }

    /**
     * Send push notification to a single user
     */
    public CompletableFuture<NotificationResponse> sendNotificationToUser(Long userId, NotificationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isEmpty()) {
                    throw new RuntimeException("User not found: " + userId);
                }

                User user = userOpt.get();
                if (user.getFcmToken() == null || user.getFcmToken().isEmpty()) {
                    log.warn("No FCM token found for user: {}", userId);
                    return NotificationResponse.builder()
                            .success(false)
                            .message("No FCM token found for user")
                            .build();
                }

                // Send FCM notification
                String messageId = sendFCMNotification(user.getFcmToken(), request);
                
                // Send WebSocket notification for real-time updates
                sendWebSocketNotification(userId, request);

                log.info("Push notification sent to user {}: {}", userId, messageId);

                return NotificationResponse.builder()
                        .success(true)
                        .messageId(messageId)
                        .recipientCount(1)
                        .sentAt(LocalDateTime.now())
                        .build();

            } catch (Exception e) {
                log.error("Error sending notification to user {}: {}", userId, e.getMessage(), e);
                return NotificationResponse.builder()
                        .success(false)
                        .message("Failed to send notification: " + e.getMessage())
                        .build();
            }
        });
    }

    /**
     * Send push notification to multiple users
     */
    public CompletableFuture<NotificationResponse> sendNotificationToUsers(List<Long> userIds, NotificationRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<User> users = userRepository.findAllById(userIds);
                List<String> fcmTokens = users.stream()
                        .filter(user -> user.getFcmToken() != null && !user.getFcmToken().isEmpty())
                        .map(User::getFcmToken)
                        .toList();

                if (fcmTokens.isEmpty()) {
                    log.warn("No valid FCM tokens found for users: {}", userIds);
                    return NotificationResponse.builder()
                            .success(false)
                            .message("No valid FCM tokens found")
                            .build();
                }

                // Send multicast FCM notification
                BatchResponse batchResponse = sendMulticastFCMNotification(fcmTokens, request);
                
                // Send WebSocket notifications
                users.forEach(user -> sendWebSocketNotification(user.getId(), request));

                log.info("Push notifications sent to {} users, success count: {}", 
                        fcmTokens.size(), batchResponse.getSuccessCount());

                return NotificationResponse.builder()
                        .success(true)
                        .messageId("multicast-" + System.currentTimeMillis())
                        .recipientCount(batchResponse.getSuccessCount())
                        .sentAt(LocalDateTime.now())
                        .build();

            } catch (Exception e) {
                log.error("Error sending notifications to users {}: {}", userIds, e.getMessage(), e);
                return NotificationResponse.builder()
                        .success(false)
                        .message("Failed to send notifications: " + e.getMessage())
                        .build();
            }
        });
    }

    /**
     * Send notification for new chat message
     */
    public void sendChatMessageNotification(Long recipientId, Long senderId, String senderName, 
                                          String messageContent, Long chatRoomId, String chatRoomName) {
        NotificationRequest request = NotificationRequest.builder()
                .title("New message from " + senderName)
                .body(messageContent.length() > 100 ? messageContent.substring(0, 100) + "..." : messageContent)
                .data(Map.of(
                    "type", "chat_message",
                    "chatRoomId", chatRoomId.toString(),
                    "chatRoomName", chatRoomName,
                    "senderId", senderId.toString(),
                    "senderName", senderName
                ))
                .build();

        sendNotificationToUser(recipientId, request);
    }

    /**
     * Send notification for car inquiry
     */
    public void sendCarInquiryNotification(Long sellerId, Long buyerId, String buyerName, 
                                         String carTitle, Long carId) {
        NotificationRequest request = NotificationRequest.builder()
                .title("New inquiry for your car")
                .body(buyerName + " is interested in " + carTitle)
                .data(Map.of(
                    "type", "car_inquiry",
                    "carId", carId.toString(),
                    "carTitle", carTitle,
                    "buyerId", buyerId.toString(),
                    "buyerName", buyerName
                ))
                .build();

        sendNotificationToUser(sellerId, request);
    }

    /**
     * Send notification for new car matching user preferences
     */
    public void sendCarRecommendationNotification(Long userId, String carTitle, Long carId, String price) {
        NotificationRequest request = NotificationRequest.builder()
                .title("New car matches your preferences")
                .body(carTitle + " - " + price)
                .data(Map.of(
                    "type", "car_recommendation",
                    "carId", carId.toString(),
                    "carTitle", carTitle,
                    "price", price
                ))
                .build();

        sendNotificationToUser(userId, request);
    }

    /**
     * Send notification for price drop
     */
    public void sendPriceDropNotification(Long userId, String carTitle, Long carId, 
                                        String oldPrice, String newPrice) {
        NotificationRequest request = NotificationRequest.builder()
                .title("Price drop alert!")
                .body(carTitle + " is now " + newPrice + " (was " + oldPrice + ")")
                .data(Map.of(
                    "type", "price_drop",
                    "carId", carId.toString(),
                    "carTitle", carTitle,
                    "oldPrice", oldPrice,
                    "newPrice", newPrice
                ))
                .build();

        sendNotificationToUser(userId, request);
    }

    /**
     * Update user FCM token
     */
    public void updateUserFCMToken(Long userId, String fcmToken) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setFcmToken(fcmToken);
                userRepository.save(user);
                log.info("Updated FCM token for user: {}", userId);
            }
        } catch (Exception e) {
            log.error("Error updating FCM token for user {}: {}", userId, e.getMessage(), e);
        }
    }

    // ========================= PRIVATE HELPER METHODS =========================

    /**
     * Send FCM notification to single device
     */
    private String sendFCMNotification(String fcmToken, NotificationRequest request) throws FirebaseMessagingException {
        if (firebaseMessaging == null) {
            throw new RuntimeException("Firebase messaging not initialized");
        }

        Notification notification = Notification.builder()
                .setTitle(request.getTitle())
                .setBody(request.getBody())
                .setImage(request.getImageUrl())
                .build();

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(notification)
                .putAllData(request.getData())
                .setAndroidConfig(buildAndroidConfig(request))
                .setApnsConfig(buildApnsConfig(request))
                .build();

        return firebaseMessaging.send(message);
    }

    /**
     * Send FCM notification to multiple devices
     */
    private BatchResponse sendMulticastFCMNotification(List<String> fcmTokens, NotificationRequest request) 
            throws FirebaseMessagingException {
        if (firebaseMessaging == null) {
            throw new RuntimeException("Firebase messaging not initialized");
        }

        Notification notification = Notification.builder()
                .setTitle(request.getTitle())
                .setBody(request.getBody())
                .setImage(request.getImageUrl())
                .build();

        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(fcmTokens)
                .setNotification(notification)
                .putAllData(request.getData())
                .setAndroidConfig(buildAndroidConfig(request))
                .setApnsConfig(buildApnsConfig(request))
                .build();

        return firebaseMessaging.sendMulticast(message);
    }

    /**
     * Build Android-specific notification config
     */
    private AndroidConfig buildAndroidConfig(NotificationRequest request) {
        return AndroidConfig.builder()
                .setTtl(3600 * 1000) // 1 hour
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                        .setIcon("ic_notification")
                        .setColor("#FF6B35")
                        .setSound("default")
                        .setClickAction("FLUTTER_NOTIFICATION_CLICK")
                        .build())
                .build();
    }

    /**
     * Build iOS-specific notification config
     */
    private ApnsConfig buildApnsConfig(NotificationRequest request) {
        return ApnsConfig.builder()
                .setAps(Aps.builder()
                        .setAlert(ApsAlert.builder()
                                .setTitle(request.getTitle())
                                .setBody(request.getBody())
                                .build())
                        .setSound("default")
                        .setBadge(1)
                        .setCategory("MESSAGE_CATEGORY")
                        .build())
                .build();
    }

    /**
     * Send WebSocket notification for real-time updates
     */
    private void sendWebSocketNotification(Long userId, NotificationRequest request) {
        try {
            Map<String, Object> wsNotification = new HashMap<>();
            wsNotification.put("type", "push_notification");
            wsNotification.put("title", request.getTitle());
            wsNotification.put("body", request.getBody());
            wsNotification.put("data", request.getData());
            wsNotification.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/notifications",
                wsNotification
            );

        } catch (Exception e) {
            log.error("Error sending WebSocket notification: {}", e.getMessage(), e);
        }
    }
}
