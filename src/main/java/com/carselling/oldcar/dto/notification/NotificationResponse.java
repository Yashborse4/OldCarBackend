package com.carselling.oldcar.dto.notification;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for push notification operations
 */
@Data
@Builder
public class NotificationResponse {

    private Boolean success;
    private String messageId;
    private String message;
    private Integer recipientCount;
    private Integer failureCount;
    private LocalDateTime sentAt;
    
    // Constructor for builder pattern
    public NotificationResponse(Boolean success, String messageId, String message, 
                              Integer recipientCount, Integer failureCount, LocalDateTime sentAt) {
        this.success = success;
        this.messageId = messageId;
        this.message = message;
        this.recipientCount = recipientCount;
        this.failureCount = failureCount;
        this.sentAt = sentAt;
    }
    
    // Helper methods for common response types
    public static NotificationResponse success(String messageId, int recipientCount) {
        return NotificationResponse.builder()
                .success(true)
                .messageId(messageId)
                .recipientCount(recipientCount)
                .sentAt(LocalDateTime.now())
                .build();
    }
    
    public static NotificationResponse failure(String errorMessage) {
        return NotificationResponse.builder()
                .success(false)
                .message(errorMessage)
                .recipientCount(0)
                .sentAt(LocalDateTime.now())
                .build();
    }
    
    public static NotificationResponse partial(String messageId, int successCount, int failureCount) {
        return NotificationResponse.builder()
                .success(failureCount == 0)
                .messageId(messageId)
                .recipientCount(successCount)
                .failureCount(failureCount)
                .message(failureCount > 0 ? "Partial success: " + failureCount + " failed" : "All notifications sent successfully")
                .sentAt(LocalDateTime.now())
                .build();
    }
}
