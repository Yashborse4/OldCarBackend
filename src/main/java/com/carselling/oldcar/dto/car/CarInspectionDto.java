package com.carselling.oldcar.dto.car;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CarInspectionDto {
    private Long id;
    private Long carId;
    private String carMake;
    private String carModel;
    private LocalDateTime scheduledDate;
    private LocalDateTime completedDate;
    private String status;
    private String inspectorName;
    private String inspectionType;
    private String report;
    private Integer score;
}
