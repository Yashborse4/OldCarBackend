package com.carselling.oldcar.dto.car;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CarComparisonDto {
    private Long id;
    private String make;
    private String model;
    private Integer year;
    private Double price;
    private Integer mileage;
    private String fuelType;
    private String transmission;
    private String condition;
    private String color;
    private Integer engineSize;
    private Integer horsepower;
    private Double rating;
    private Integer viewCount;
}
