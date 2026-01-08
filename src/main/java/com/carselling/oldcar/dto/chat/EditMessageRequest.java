package com.carselling.oldcar.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request DTO for editing messages
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EditMessageRequest {

    @NotBlank(message = "New content is required")
    @Size(min = 1, max = 2000, message = "Message content must be between 1 and 2000 characters")
    private String newContent;

    @Override
    public String toString() {
        return "EditMessageRequest{" +
                "newContent='" + newContent + '\'' +
                '}';
    }
}
