package com.carselling.oldcar.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token Validation Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidationResponse {
    private boolean valid;
    private UserDetails userDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDetails {
        private Long userId;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private String role;
        private String location;
        private String dealerName;
        private String showroomName;
        private String address;
        private String city;
        private Double latitude;
        private Double longitude;
        private String phoneNumber;
        private String profileImageUrl;

        /**
         * Whether the user's email has been verified.
         */
        private Boolean emailVerified;

        /**
         * Whether the user has been verified as a dealer.
         */
        private Boolean verifiedDealer;
        private String dealerStatus; // Added to support granular status display
        private String dealerStatusDisplayName;
        private String dealerStatusReason;
        private Boolean onboardingCompleted; // Whether user completed preference onboarding
    }
}
