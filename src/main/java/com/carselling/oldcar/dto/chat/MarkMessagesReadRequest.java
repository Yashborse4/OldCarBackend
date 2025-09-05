package com.carselling.oldcar.dto.chat;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for marking messages as read
 */
public class MarkMessagesReadRequest {

    @NotEmpty(message = "At least one message ID is required")
    @Size(min = 1, max = 100, message = "Can mark maximum 100 messages at once")
    private List<Long> messageIds;

    // Constructors
    public MarkMessagesReadRequest() {}

    public MarkMessagesReadRequest(List<Long> messageIds) {
        this.messageIds = messageIds;
    }

    // Getters and Setters
    public List<Long> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(List<Long> messageIds) {
        this.messageIds = messageIds;
    }

    @Override
    public String toString() {
        return "MarkMessagesReadRequest{" +
                "messageIds=" + messageIds +
                '}';
    }
}
