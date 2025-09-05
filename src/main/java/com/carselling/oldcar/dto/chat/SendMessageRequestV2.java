package com.carselling.oldcar.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Enhanced Send Message Request DTO V2 with reply and file support
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequestV2 {
    
    @NotNull(message = "Chat ID is required")
    private Long chatId;
    
    @NotBlank(message = "Message content is required")
    private String content;
    
    @Builder.Default
    private String messageType = "TEXT";
    
    private Long replyToId;
    
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String mimeType;
}
