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
@io.swagger.v3.oas.annotations.media.Schema(description = "User Response Data")
public class UserResponse {

    @io.swagger.v3.oas.annotations.media.Schema(example = "1", description = "User ID")
    private Long id;
    @io.swagger.v3.oas.annotations.media.Schema(example = "johndoe", description = "Username")
    private String username;
    @io.swagger.v3.oas.annotations.media.Schema(example = "john@example.com", description = "Email")
    private String email;
    private String firstName;
    private String lastName;
    @io.swagger.v3.oas.annotations.media.Schema(example = "USER", description = "User Role")
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
    private Double latitude;
    private Double longitude;
    private Boolean onboardingCompleted;

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
