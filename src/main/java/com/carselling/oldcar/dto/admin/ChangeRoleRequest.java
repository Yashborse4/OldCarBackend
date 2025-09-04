package com.carselling.oldcar.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Change Role Request DTO for admin operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangeRoleRequest {

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^(VIEWER|SELLER|DEALER|ADMIN)$", message = "Role must be VIEWER, SELLER, DEALER, or ADMIN")
    private String newRole;
    
    private String reason; // Optional reason for role change
}
