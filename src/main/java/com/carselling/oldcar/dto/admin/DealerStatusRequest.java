package com.carselling.oldcar.dto.admin;

import com.carselling.oldcar.model.DealerStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating dealer status by admin.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerStatusRequest {

    @NotNull(message = "Status is required")
    private DealerStatus status;

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
