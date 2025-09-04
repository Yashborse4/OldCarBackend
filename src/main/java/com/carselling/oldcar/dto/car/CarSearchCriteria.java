package com.carselling.oldcar.dto.car;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Car Search Criteria DTO for advanced filtering
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarSearchCriteria {
    private String make;
    private String model;
    private Integer minYear;
    private Integer maxYear;
    private Long minPrice;
    private Long maxPrice;
    private String location;
    private String condition;
    private String status;
    private Boolean featured;
    private String fuelType;
    private String transmission;
    private String bodyType;
    private String color;
    private Integer minSeatingCapacity;
    private Integer maxSeatingCapacity;
    private Long minMileage;
    private Long maxMileage;
    private String dealerId;
    private String sortBy;
    private String sortDirection;
}
