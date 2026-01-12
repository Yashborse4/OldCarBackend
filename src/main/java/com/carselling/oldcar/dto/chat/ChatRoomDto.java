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
    private String name;
    private String description;
    private String type;
    private CreatedBy createdBy;
    private boolean isActive;
    private Long carId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActivityAt;
    private int participantCount;
    private LastMessage lastMessage;
    private Integer maxParticipants;

    // Inquiry specific fields
    private String status;
    private Integer leadScore;
    private String buyerName;
    private String buyerPhone;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatedBy {
        private Long id;
        private String username;
        private String email;
        private String displayName;
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
        }
    }

    private CarInfo carInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CarInfo {
        private Long id;
        private String title;
        private Double price;
        private String imageUrl;
    }
}
