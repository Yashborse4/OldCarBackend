package com.carselling.oldcar.dto.car;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CarTestDriveDto {
    private Long id;
    private Long carId;
    private String carMake;
    private String carModel;
    private LocalDateTime scheduledDate;
    private LocalDateTime confirmedDate;
    private LocalDateTime cancelledDate;
    private String status;
    private String contactNumber;
    private String message;
}
