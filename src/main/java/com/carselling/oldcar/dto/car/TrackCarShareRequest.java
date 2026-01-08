package com.carselling.oldcar.dto.car;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackCarShareRequest {

    @NotBlank(message = "Platform is required")
    @Size(max = 50, message = "Platform must not exceed 50 characters")
    private String platform;
}

