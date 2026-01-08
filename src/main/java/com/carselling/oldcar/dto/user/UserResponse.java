package com.carselling.oldcar.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * User Response DTO for public user information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private String location;
    private String phoneNumber;
    private Boolean isActive;
    private Boolean isEmailVerified;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String profileImageUrl; // URL endpoint to fetch the profile image blob

    // Dealer status fields (only relevant for DEALER role)
    private String dealerStatus;
    private LocalDateTime dealerStatusUpdatedAt;
    private String dealerStatusReason;

    // Dealer profile fields
    private String dealerName;
    private String showroomName;
    private String address;
    private String city;

    // Helper method
    public String getDisplayName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else {
            return username;
        }
    }
}
