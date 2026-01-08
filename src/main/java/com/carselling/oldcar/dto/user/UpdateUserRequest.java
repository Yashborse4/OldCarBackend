package com.carselling.oldcar.dto.user;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Update User Request DTO for profile updates
 * Note: Email and phone number are NOT editable for security reasons
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;

    @Size(max = 100, message = "Location must not exceed 100 characters")
    private String location;

    // Dealer-specific fields
    @Size(max = 100, message = "Dealer name must not exceed 100 characters")
    private String dealerName;

    @Size(max = 100, message = "Showroom name must not exceed 100 characters")
    private String showroomName;

    @Size(max = 255, message = "Address must not exceed 255 characters")
    private String address;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;
}
