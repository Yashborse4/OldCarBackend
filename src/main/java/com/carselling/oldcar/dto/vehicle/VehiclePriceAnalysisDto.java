package com.carselling.oldcar.dto.vehicle;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO for vehicle price analysis
 */
@Data
@Builder
public class VehiclePriceAnalysisDto {
    
    private BigDecimal averagePrice;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal priceRange;
    private Long totalListings;
    private String recommendation;
    
    public VehiclePriceAnalysisDto(BigDecimal averagePrice, BigDecimal minPrice, BigDecimal maxPrice,
                                  BigDecimal priceRange, Long totalListings, String recommendation) {
        this.averagePrice = averagePrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.priceRange = priceRange;
        this.totalListings = totalListings;
        this.recommendation = recommendation;
    }
}
