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
        private String role;
        private String location;

        /**
         * Whether the user's email has been verified.
         */
        private Boolean emailVerified;

        /**
         * Whether the user has been verified as a dealer.
         */
        private Boolean verifiedDealer;
    }
}
