package com.carselling.oldcar.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Chat Message information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private Long id;
    private Long chatRoomId;
    private Sender sender;
    private String content;
    private String messageType;
    private boolean isEdited;
    private boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime editedAt;
    private ReplyTo replyTo;
    private FileAttachment fileAttachment;
    private String deliveryStatus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sender {
        private Long id;
        private String username;
        private String displayName;
        private String avatarUrl;
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
}
