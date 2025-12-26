package com.carselling.oldcar.dto.car;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Public Car DTO - Contains only non-sensitive car information
 * Safe for anonymous access
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PublicCarDTO {
    private Long id;
    private String make;
    private String model;
    private Integer year;
    private BigDecimal price;
    private String imageUrl;
    private String city; // Derived from location
    private String fuelType;
    private String transmission;
    private Integer mileage;
    private LocalDateTime createdAt;

    // Helper to get full title
    public String getTitle() {
        return year + " " + make + " " + model;
    }
}
