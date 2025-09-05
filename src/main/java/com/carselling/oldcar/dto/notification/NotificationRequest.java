package com.carselling.oldcar.dto.notification;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Request DTO for push notifications
 */
@Data
@Builder
public class NotificationRequest {

    private String title;
    private String body;
    private String imageUrl;
    
    @Builder.Default
    private Map<String, String> data = new HashMap<>();
    
    // Platform-specific settings
    private String sound;
    private Integer badge;
    private String clickAction;
    
    // Delivery settings
    private Boolean highPriority;
    private Long timeToLive; // in seconds
    
    // Constructor for builder pattern
    public NotificationRequest(String title, String body, String imageUrl, Map<String, String> data,
                             String sound, Integer badge, String clickAction, Boolean highPriority, Long timeToLive) {
        this.title = title;
        this.body = body;
        this.imageUrl = imageUrl;
        this.data = data != null ? data : new HashMap<>();
        this.sound = sound;
        this.badge = badge;
        this.clickAction = clickAction;
        this.highPriority = highPriority;
        this.timeToLive = timeToLive;
    }
    
    // Helper methods for common notification types
    public static NotificationRequest chatMessage(String senderName, String message, Long chatRoomId) {
        return NotificationRequest.builder()
                .title("New message from " + senderName)
                .body(message)
                .highPriority(true)
                .sound("default")
                .data(Map.of(
                    "type", "chat_message",
                    "chatRoomId", chatRoomId.toString(),
                    "senderName", senderName
                ))
                .build();
    }
    
    public static NotificationRequest carInquiry(String buyerName, String carTitle, Long carId) {
        return NotificationRequest.builder()
                .title("New inquiry for your car")
                .body(buyerName + " is interested in " + carTitle)
                .highPriority(true)
                .sound("default")
                .data(Map.of(
                    "type", "car_inquiry",
                    "carId", carId.toString(),
                    "carTitle", carTitle,
                    "buyerName", buyerName
                ))
                .build();
    }
    
    public static NotificationRequest priceAlert(String carTitle, String newPrice, Long carId) {
        return NotificationRequest.builder()
                .title("Price drop alert!")
                .body(carTitle + " is now " + newPrice)
                .highPriority(true)
                .sound("default")
                .data(Map.of(
                    "type", "price_drop",
                    "carId", carId.toString(),
                    "carTitle", carTitle,
                    "newPrice", newPrice
                ))
                .build();
    }
}
