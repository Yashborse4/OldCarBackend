package com.carselling.oldcar.dto.email;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO for email responses
 */
@Data
@Builder
public class EmailResponse {

    private Boolean success;
    private String recipient;
    private String subject;
    private String messageId;
    private String errorMessage;
    private LocalDateTime sentAt;
    private String status; // SENT, FAILED, PENDING, DELIVERED
    private Integer retryCount;

    public EmailResponse(Boolean success, String recipient, String subject, String messageId, 
                        String errorMessage, LocalDateTime sentAt, String status, Integer retryCount) {
        this.success = success;
        this.recipient = recipient;
        this.subject = subject;
        this.messageId = messageId;
        this.errorMessage = errorMessage;
        this.sentAt = sentAt;
        this.status = status;
        this.retryCount = retryCount;
    }

    // Helper methods
    public static EmailResponse success(String recipient, String subject, String messageId) {
        return EmailResponse.builder()
                .success(true)
                .recipient(recipient)
                .subject(subject)
                .messageId(messageId)
                .status("SENT")
                .sentAt(LocalDateTime.now())
                .build();
    }

    public static EmailResponse failure(String recipient, String errorMessage) {
        return EmailResponse.builder()
                .success(false)
                .recipient(recipient)
                .errorMessage(errorMessage)
                .status("FAILED")
                .sentAt(LocalDateTime.now())
                .build();
    }
}
