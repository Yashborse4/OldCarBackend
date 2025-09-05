package com.carselling.oldcar.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Message Update DTO for real-time message updates (edit, delete, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageUpdateDto {
    private String action; // EDITED, DELETED, etc.
    private ChatMessageDtoV2 message;
    private LocalDateTime timestamp;
}
