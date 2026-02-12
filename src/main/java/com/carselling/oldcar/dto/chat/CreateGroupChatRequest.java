package com.carselling.oldcar.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Create Group Chat Request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupChatRequest {

    @NotBlank(message = "Group name is required")
    @Size(max = 200, message = "Group name cannot exceed 200 characters")
    private String name;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Builder.Default
    private String type = "GROUP"; // GROUP, DEALER_ONLY

    @Builder.Default
    private Integer maxParticipants = 50;

    private List<Long> participantIds;
}
