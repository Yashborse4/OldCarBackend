package com.carselling.oldcar.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Chat Room information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDto {
    private Long id;
    private Long carId;
    private String carTitle;
    private String carImageUrl;
    private Long buyerId;
    private String buyerName;
    private String buyerAvatarUrl;
    private Long sellerId;
    private String sellerName;
    private String sellerAvatarUrl;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime lastMessageAt;
    private long unreadCount;
}
