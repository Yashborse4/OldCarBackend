package com.carselling.oldcar.dto.car;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Enhanced Car Response DTO V2 with analytics and co-listing features
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarResponseV2 {
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
        private String engineCapacity;
        private Integer seatingCapacity;
        private List<String> features;
        private String color;
        private String bodyType;
        private Integer doors;
        private String driveType;
        private Map<String, Object> additionalSpecs;
    }
}
