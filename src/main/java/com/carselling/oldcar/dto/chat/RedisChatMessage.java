package com.carselling.oldcar.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO for synchronizing WebSocket messages across multiple nodes via Redis Pub/Sub.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisChatMessage implements Serializable {
    private String type; // MESSAGE, UPDATE, TYPING, READ_RECEIPT
    private Long chatRoomId;
    private Object payload; // The actual DTO (ChatMessageDto, MessageUpdateDto, etc.)
    private String originNodeId; // Optional: to identify which node sent the message
}
