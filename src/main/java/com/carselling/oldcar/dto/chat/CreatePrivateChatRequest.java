package com.carselling.oldcar.dto.chat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create Private Chat Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePrivateChatRequest {
    
    @NotNull(message = "Other user ID is required")
    private Long otherUserId;
    
    @Size(max = 200, message = "Chat name cannot exceed 200 characters")
    private String name;
}
