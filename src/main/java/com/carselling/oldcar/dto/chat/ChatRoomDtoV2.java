package com.carselling.oldcar.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Enhanced Chat Room DTO V2 for comprehensive chat system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDtoV2 {
    private Long id;
    private String name;
    private String description;
    private String type;
    private CreatedBy createdBy;
    private boolean isActive;
    private Long carId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActivityAt;
    private Integer participantCount;
    private Long unreadCount;
    private LastMessage lastMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatedBy {
        private Long id;
        private String username;
        private String email;
        private String profileImage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LastMessage {
        private Long id;
        private String content;
        private String messageType;
        private Sender sender;
        private LocalDateTime createdAt;
        
        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Sender {
            private Long id;
            private String username;
            private String profileImage;
        }
    }
}
