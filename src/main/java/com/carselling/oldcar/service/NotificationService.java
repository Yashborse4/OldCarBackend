package com.carselling.oldcar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Stub NotificationService for compilation
 * Full implementation is in the disabled folder
 */
@Slf4j
@Service
public class NotificationService {
    
    /**
     * Send simple notification - stub implementation
     */
    public void sendNotification(Long userId, String title, String message) {
        log.info("Notification stub: User {}: {} - {}", userId, title, message);
    }
    
    /**
     * Send notification with data - stub implementation
     */
    public void sendNotification(Long userId, String title, String message, Map<String, Object> data) {
        log.info("Notification stub: User {}: {} - {} with data: {}", userId, title, message, data);
    }
    
    /**
     * Register device token - stub implementation
     */
    public void registerDeviceToken(Long userId, String deviceToken) {
        log.info("Device token registration stub: User {} token {}", userId, deviceToken);
    }
    
    /**
     * Unregister device token - stub implementation
     */
    public void unregisterDeviceToken(Long userId, String deviceToken) {
        log.info("Device token unregistration stub: User {} token {}", userId, deviceToken);
    }
}
