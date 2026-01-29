package com.carselling.oldcar.dto.car;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Car Response DTO with comprehensive car and dealer information
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarResponse {

    private String id;
    private String make;
    private String model;
    private Integer year;
    private Long price;
    private Long mileage;
    private String location;
    private String condition;
    private String description;
    @Builder.Default
    private List<String> images = new ArrayList<>();
    private String videoUrl;
    private CarSpecifications specifications;
    private String dealerId;
    private String dealerName;
    private boolean isCoListed;
    @Builder.Default
    private List<String> coListedIn = new ArrayList<>();
    private Long views;
    private Long inquiries;
    private Long shares;
    private String status;
    private String mediaStatus;
    private Boolean isSold;
    private Boolean isAvailable;
    private Boolean isFeatured;
    private Long carMasterId; // Reference to catalog entry, if linked
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CarSpecifications {
        private String fuelType;
        private String transmission;
        private String color;
        private String engine;
        private String driveType;
        private Integer doors;
        private Integer seats;
    }

    // Helper methods
    public String getFullName() {
        return year + " " + make + " " + model;
    }

    public boolean isAvailable() {
        return "Available".equals(status);
    }
}
