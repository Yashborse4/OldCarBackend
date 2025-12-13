package com.carselling.oldcar.dto.email;

import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * DTO for email requests
 */
@Data
@Builder
public class EmailRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    private String recipient;

    @NotBlank(message = "Subject is required")
    @Size(max = 200, message = "Subject cannot exceed 200 characters")
    private String subject;

    @NotBlank(message = "Content is required")
    @Size(max = 10000, message = "Content cannot exceed 10000 characters")
    private String content;

    private String templateType; // VERIFICATION, PASSWORD_RESET, WELCOME, etc.
    private Map<String, String> templateVariables;
    private Boolean isHtml;
    private String priority; // LOW, NORMAL, HIGH

    public EmailRequest(String recipient, String subject, String content, String templateType, 
                       Map<String, String> templateVariables, Boolean isHtml, String priority) {
        this.recipient = recipient;
        this.subject = subject;
        this.content = content;
        this.templateType = templateType;
        this.templateVariables = templateVariables;
        this.isHtml = isHtml != null ? isHtml : true;
        this.priority = priority != null ? priority : "NORMAL";
    }
}
