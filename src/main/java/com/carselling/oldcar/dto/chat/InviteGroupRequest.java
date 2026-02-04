package com.carselling.oldcar.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InviteGroupRequest {
    @NotNull(message = "Chat ID is required")
    private Long chatId;

    @NotBlank(message = "Username is required")
    private String username;
}
