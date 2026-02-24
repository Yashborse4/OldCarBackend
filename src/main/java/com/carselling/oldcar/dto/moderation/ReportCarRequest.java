package com.carselling.oldcar.dto.moderation;

import com.carselling.oldcar.model.CarReport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportCarRequest {

    @NotBlank(message = "Car ID is required")
    private String carId;

    @NotNull(message = "Report reason is required")
    private CarReport.CarReportReason reason;

    private String additionalComments;
}