package com.carselling.oldcar.dto.car;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CarAlertDto {
    private Long id;
    private String make;
    private String model;
    private Integer minYear;
    private Integer maxYear;
    private Double minPrice;
    private Double maxPrice;
    private Boolean isActive;
    private LocalDateTime createdDate;
}
