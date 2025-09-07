package com.carselling.oldcar.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for typing indicators in WebSocket communication
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingIndicatorDto {

    private Long chatId; // Changed from chatRoomId to match ChatService usage
    private Long userId;
    private String userName;
    private Boolean isTyping;
    private java.time.LocalDateTime timestamp; // Changed to LocalDateTime to match ChatService usage
}
