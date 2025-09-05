package com.carselling.oldcar.dto.vehicle;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO for advanced vehicle search criteria
 */
@Data
@Builder
public class VehicleSearchCriteria {
    
    // Basic filters
    private String make;
    private String model;
    private Integer yearFrom;
    private Integer yearTo;
    private BigDecimal priceFrom;
    private BigDecimal priceTo;
    private Integer mileageFrom;
    private Integer mileageTo;
    
    // Category filters
    private String fuelType;
    private String transmission;
    private String bodyType;
    private String color;
    private String condition;
    
    // Location filters
    private String city;
    private String state;
    private Double latitude;
    private Double longitude;
    private Double radiusKm;
    
    // Advanced filters
    private Boolean isAvailable;
    private Boolean isFeatured;
    private Boolean hasImages;
    private Boolean hasWarranty;
    
    // Sorting options
    private String sortBy; // price, year, mileage, createdAt
    private String sortOrder; // asc, desc
    
    // Search query
    private String query; // Free text search
    
    // Constructor for builder pattern
    public VehicleSearchCriteria(String make, String model, Integer yearFrom, Integer yearTo,
                                BigDecimal priceFrom, BigDecimal priceTo, Integer mileageFrom, Integer mileageTo,
                                String fuelType, String transmission, String bodyType, String color, String condition,
                                String city, String state, Double latitude, Double longitude, Double radiusKm,
                                Boolean isAvailable, Boolean isFeatured, Boolean hasImages, Boolean hasWarranty,
                                String sortBy, String sortOrder, String query) {
        this.make = make;
        this.model = model;
        this.yearFrom = yearFrom;
        this.yearTo = yearTo;
        this.priceFrom = priceFrom;
        this.priceTo = priceTo;
        this.mileageFrom = mileageFrom;
        this.mileageTo = mileageTo;
        this.fuelType = fuelType;
        this.transmission = transmission;
        this.bodyType = bodyType;
        this.color = color;
        this.condition = condition;
        this.city = city;
        this.state = state;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusKm = radiusKm;
        this.isAvailable = isAvailable;
        this.isFeatured = isFeatured;
        this.hasImages = hasImages;
        this.hasWarranty = hasWarranty;
        this.sortBy = sortBy;
        this.sortOrder = sortOrder;
        this.query = query;
    }
    
    // Helper methods
    public boolean hasLocationFilter() {
        return latitude != null && longitude != null && radiusKm != null;
    }
    
    public boolean hasPriceRange() {
        return priceFrom != null || priceTo != null;
    }
    
    public boolean hasYearRange() {
        return yearFrom != null || yearTo != null;
    }
    
    public boolean hasMileageRange() {
        return mileageFrom != null || mileageTo != null;
    }
}
