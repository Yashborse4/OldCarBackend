package com.carselling.oldcar.dto.vehicle;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO for vehicle summary (used in lists and cards)
 */
@Data
@Builder
public class VehicleSummaryDto {
    
    private Long id;
    private String make;
    private String model;
    private Integer year;
    private BigDecimal price;
    private Integer mileage;
    private String primaryImage;
    private String location;
    private Boolean isAvailable;
    private Boolean isFeatured;
    private Integer viewCount;
    
    // Constructor
    public VehicleSummaryDto(Long id, String make, String model, Integer year, BigDecimal price,
                           Integer mileage, String primaryImage, String location, Boolean isAvailable,
                           Boolean isFeatured, Integer viewCount) {
        this.id = id;
        this.make = make;
        this.model = model;
        this.year = year;
        this.price = price;
        this.mileage = mileage;
        this.primaryImage = primaryImage;
        this.location = location;
        this.isAvailable = isAvailable;
        this.isFeatured = isFeatured;
        this.viewCount = viewCount;
    }
    
    public String getVehicleTitle() {
        return year + " " + make + " " + model;
    }
    
    public String getFormattedPrice() {
        if (price == null) return "N/A";
        return "$" + String.format("%,.0f", price);
    }
}
