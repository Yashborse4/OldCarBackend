package com.carselling.oldcar.dto.car;

import com.carselling.oldcar.model.MediaStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMediaStatusRequest {
    @NotNull(message = "Status is required")
    private MediaStatus status;
}
