package com.carselling.oldcar.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for sending a message
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotNull(message = "Chat ID is required")
    private Long chatId;

    @NotBlank(message = "Message content cannot be empty")
    @Size(max = 2000, message = "Message content cannot exceed 2000 characters")
    private String content;

    @Builder.Default
    private String messageType = "TEXT";

    private Long replyToId;

    private String clientMessageId;

    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mimeType;
}
