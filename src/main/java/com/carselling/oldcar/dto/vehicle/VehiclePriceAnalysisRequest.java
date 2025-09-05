package com.carselling.oldcar.dto.vehicle;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO for vehicle price analysis request
 */
@Data
@Builder
public class VehiclePriceAnalysisRequest {
    
    private String make;
    private String model;
    private Integer yearFrom;
    private Integer yearTo;
    private Integer mileageFrom;
    private Integer mileageTo;
    private BigDecimal currentPrice;
    
    public VehiclePriceAnalysisRequest(String make, String model, Integer yearFrom, Integer yearTo,
                                     Integer mileageFrom, Integer mileageTo, BigDecimal currentPrice) {
        this.make = make;
        this.model = model;
        this.yearFrom = yearFrom;
        this.yearTo = yearTo;
        this.mileageFrom = mileageFrom;
        this.mileageTo = mileageTo;
        this.currentPrice = currentPrice;
    }
}
