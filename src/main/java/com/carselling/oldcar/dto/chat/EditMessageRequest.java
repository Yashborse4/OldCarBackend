package com.carselling.oldcar.dto.chat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Request DTO for editing messages
 */
public class EditMessageRequest {

    @NotBlank(message = "New content is required")
    @Size(min = 1, max = 2000, message = "Message content must be between 1 and 2000 characters")
    private String newContent;

    // Constructors
    public EditMessageRequest() {}

    public EditMessageRequest(String newContent) {
        this.newContent = newContent;
    }

    // Getters and Setters
    public String getNewContent() {
        return newContent;
    }

    public void setNewContent(String newContent) {
        this.newContent = newContent;
    }

    @Override
    public String toString() {
        return "EditMessageRequest{" +
                "newContent='" + newContent + '\'' +
                '}';
    }
}
