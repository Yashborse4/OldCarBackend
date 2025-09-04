package com.carselling.oldcar.dto.chat;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new chat room
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateChatRoomRequest {
    
    @NotNull(message = "Car ID is required")
    private Long carId;
    
    @NotNull(message = "Seller ID is required")
    private Long sellerId;
}
