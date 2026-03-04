package com.carselling.oldcar.dto.car;

import com.carselling.oldcar.model.CarReport.CarReportReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarReportRequest {
    private CarReportReason reason;
    private String comments;
}
