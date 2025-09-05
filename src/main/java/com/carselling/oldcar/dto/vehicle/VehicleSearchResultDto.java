package com.carselling.oldcar.dto.vehicle;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for vehicle search results
 */
@Data
@Builder
public class VehicleSearchResultDto {
    
    private Long id;
    private String make;
    private String model;
    private Integer year;
    private BigDecimal price;
    private Integer mileage;
    private String fuelType;
    private String transmission;
    private String bodyType;
    private String color;
    private String condition;
    private String description;
    
    // Images and media
    private List<String> images;
    private String primaryImage;
    
    // Location info
    private String location;
    private String city;
    private String state;
    private Double latitude;
    private Double longitude;
    private Double distanceKm; // Distance from search location
    
    // Seller info
    private String sellerName;
    private String sellerPhone;
    private String sellerEmail;
    private Boolean isVerifiedSeller;
    
    // Status and metadata
    private Boolean isAvailable;
    private Boolean isFeatured;
    private Boolean hasWarranty;
    private Integer viewCount;
    private Integer favoriteCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Additional features
    private List<String> features; // Air conditioning, GPS, etc.
    private String vinNumber;
    private Integer ownerCount;
    
    // Constructor for builder pattern
    public VehicleSearchResultDto(Long id, String make, String model, Integer year, BigDecimal price,
                                 Integer mileage, String fuelType, String transmission, String bodyType,
                                 String color, String condition, String description, List<String> images,
                                 String primaryImage, String location, String city, String state,
                                 Double latitude, Double longitude, Double distanceKm, String sellerName,
                                 String sellerPhone, String sellerEmail, Boolean isVerifiedSeller,
                                 Boolean isAvailable, Boolean isFeatured, Boolean hasWarranty,
                                 Integer viewCount, Integer favoriteCount, LocalDateTime createdAt,
                                 LocalDateTime updatedAt, List<String> features, String vinNumber,
                                 Integer ownerCount) {
        this.id = id;
        this.make = make;
        this.model = model;
        this.year = year;
        this.price = price;
        this.mileage = mileage;
        this.fuelType = fuelType;
        this.transmission = transmission;
        this.bodyType = bodyType;
        this.color = color;
        this.condition = condition;
        this.description = description;
        this.images = images;
        this.primaryImage = primaryImage;
        this.location = location;
        this.city = city;
        this.state = state;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceKm = distanceKm;
        this.sellerName = sellerName;
        this.sellerPhone = sellerPhone;
        this.sellerEmail = sellerEmail;
        this.isVerifiedSeller = isVerifiedSeller;
        this.isAvailable = isAvailable;
        this.isFeatured = isFeatured;
        this.hasWarranty = hasWarranty;
        this.viewCount = viewCount;
        this.favoriteCount = favoriteCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.features = features;
        this.vinNumber = vinNumber;
        this.ownerCount = ownerCount;
    }
    
    // Helper methods
    public String getFormattedPrice() {
        if (price == null) return "N/A";
        return "$" + String.format("%,.0f", price);
    }
    
    public String getFormattedMileage() {
        if (mileage == null) return "N/A";
        return String.format("%,d km", mileage);
    }
    
    public String getVehicleTitle() {
        return year + " " + make + " " + model;
    }
    
    public String getFormattedDistance() {
        if (distanceKm == null) return "";
        return String.format("%.1f km away", distanceKm);
    }
}
