package com.carselling.oldcar.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Lightweight DTO for Chat Room Lists
 * Solves "Over-fetching" problem by excluding heavy details like full
 * participants list.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomListDto {
    private Long id;
    private String name;
    private String type;
    private String carName;
    private String carImageUrl;
    private Long unreadCount;
    private LocalDateTime lastActivityAt;

    // Minimal last message info
    private String lastMessageContent;
    private String lastMessageType;
    private String lastMessageSender;
    private LocalDateTime lastMessageTime;
}
