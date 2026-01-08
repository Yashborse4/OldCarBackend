package com.carselling.oldcar.dto.car;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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
    private List<String> images;
    private CarSpecifications specifications;
    private String dealerId;
    private String dealerName;
    private boolean isCoListed;
    private List<String> coListedIn;
    private Long views;
    private Long inquiries;
    private Long shares;
    private String status;
    private boolean featured;
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
