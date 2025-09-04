package com.carselling.oldcar.dto.car;

import com.carselling.oldcar.dto.user.UserSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Car Response DTO with comprehensive car and owner information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarResponse {

    private Long id;
    private String make;
    private String model;
    private Integer year;
    private BigDecimal price;
    private String description;
    private String imageUrl;
    private UserSummary owner;
    private Boolean isActive;
    private Boolean isFeatured;
    private Boolean isSold;
    private Long viewCount;
    private Integer mileage;
    private String fuelType;
    private String transmission;
    private String color;
    private String vin;
    private Integer numberOfOwners;
    private LocalDateTime featuredUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Helper methods
    public String getFullName() {
        return year + " " + make + " " + model;
    }

    public boolean isCurrentlyFeatured() {
        return Boolean.TRUE.equals(isFeatured) && 
               (featuredUntil == null || featuredUntil.isAfter(LocalDateTime.now()));
    }

    public boolean isAvailable() {
        return Boolean.TRUE.equals(isActive) && !Boolean.TRUE.equals(isSold);
    }
}
