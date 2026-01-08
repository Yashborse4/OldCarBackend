package com.carselling.oldcar.dto.auth;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registration Request DTO with comprehensive validation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {



    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.@-]+$", message = "Username can only contain letters, numbers, underscores, dots, hyphens, and @")
    private String username; // Optional - will be derived from email if not provided

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String password;

    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String firstName;

    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;

    /**
     * Optional role selected during registration.
     * Only USER and DEALER are allowed values for self-registration.
     */
    @Pattern(regexp = "^(USER|DEALER)$", message = "Role must be USER or DEALER")
    private String role;

    @Size(max = 100, message = "Location must not exceed 100 characters")
    private String location;

    @Pattern(regexp = "^[+]?[1-9]\\d{1,14}$|^$", message = "Please provide a valid phone number")
    private String phoneNumber;
}
