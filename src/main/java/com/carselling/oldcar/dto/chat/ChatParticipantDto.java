package com.carselling.oldcar.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Chat Participant DTO for representing participants in chat rooms
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatParticipantDto {
    private Long id;
    private UserInfo user;
    private String role;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    private LocalDateTime lastActivityAt;
    private boolean isActive;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String displayName;
        private String profileImage;
        private String systemRole; // DEALER, USER, ADMIN etc.
        private String phoneNumber;
    }

    // Convenience method for ChatWebSocketController
    public Long getUserId() {
        return this.user != null ? this.user.getId() : null;
    }
}
