package com.carselling.oldcar.dto.chat;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for adding participants to a chat room
 */
public class AddParticipantsRequest {

    @NotEmpty(message = "At least one user ID is required")
    @Size(min = 1, max = 50, message = "Can add maximum 50 participants at once")
    private List<Long> userIds;

    // Constructors
    public AddParticipantsRequest() {}

    public AddParticipantsRequest(List<Long> userIds) {
        this.userIds = userIds;
    }

    // Getters and Setters
    public List<Long> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<Long> userIds) {
        this.userIds = userIds;
    }

    @Override
    public String toString() {
        return "AddParticipantsRequest{" +
                "userIds=" + userIds +
                '}';
    }
}
