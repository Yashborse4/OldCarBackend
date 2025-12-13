package com.carselling.oldcar.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for banning/unbanning users (Admin operation)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BanUserRequest {
    
    @Builder.Default
    private boolean banned = true; // true to ban, false to unban
    
    @NotBlank(message = "Reason is required")
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    private String reason;
}
