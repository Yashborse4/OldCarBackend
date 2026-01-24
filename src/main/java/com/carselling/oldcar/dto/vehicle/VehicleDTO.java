package com.carselling.oldcar.dto.vehicle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Vehicle Data Transfer Object for batch processing operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleDTO {

    private Long id;
    private String make;
    private String model;
    private Integer year;
    private BigDecimal price;
    private String description;
    private String imageUrl;
    private List<String> images;
    private Double mileage;
    private String fuelType;
    private String transmission;
    private String color;
    private Integer numberOfOwners;
    private String condition;
    private String location;

    // Owner information
    private Long ownerId;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;

    // Status information
    private Boolean isActive;
    private Boolean isFeatured;
    private Boolean isSold;
    private Long viewCount;
    private Long inquiryCount;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime featuredUntil;

    // Additional metadata for batch processing
    private String status;
    private String batchId;
    private String source; // API, CSV, EXCEL, etc.
    private String validationStatus;
    private List<String> validationErrors;

    // Helper methods
    public String getFullName() {
        return String.format("%d %s %s", year, make, model);
    }

    public boolean hasErrors() {
        return validationErrors != null && !validationErrors.isEmpty();
    }

    public boolean isValid() {
        return "VALID".equals(validationStatus);
    }

    public void addValidationError(String error) {
        if (validationErrors == null) {
            validationErrors = new java.util.ArrayList<>();
        }
        validationErrors.add(error);
        this.validationStatus = "INVALID";
    }
}
