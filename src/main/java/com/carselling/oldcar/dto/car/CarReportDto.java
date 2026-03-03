package com.carselling.oldcar.dto.car;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CarReportDto {
    private Long id;
    private Long carId;
    private String carMake;
    private String carModel;
    private Integer carYear;
    private Double marketPrice;
    private Integer conditionScore;
    private String recommendation;
    private LocalDateTime reportDate;
}
