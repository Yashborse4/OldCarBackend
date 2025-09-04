package com.carselling.oldcar.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User Summary DTO for minimal user information in nested responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummary {

    private Long id;
    private String username;
    private String role;
    private String location;

    // Helper method
    public String getDisplayName() {
        return username;
    }
}
