package com.carselling.oldcar.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Enhanced Chat Message DTO V2 for comprehensive messaging
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDtoV2 {
    private Long id;
    private Long chatId;
    private Sender sender;
    private String content;
    private String messageType;
    private ReplyTo replyTo;
    private boolean isEdited;
    private boolean isDeleted;
    private FileAttachment fileAttachment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime editedAt;
    private String deliveryStatus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sender {
        private Long id;
        private String username;
        private String email;
        private String profileImage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplyTo {
        private Long id;
        private String content;
        private String senderUsername;
        private String messageType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileAttachment {
        private String fileUrl;
        private String fileName;
        private Long fileSize;
        private String mimeType;
    }
    
    // Compatibility method for ChatService
    public Long getChatRoomId() {
        return this.chatId;
    }
}
