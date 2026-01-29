package com.carselling.oldcar.dto.car;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Car Request DTO for creating and updating cars
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarRequest {

    @NotBlank(message = "Make is required")
    @Size(min = 2, max = 50, message = "Make must be between 2 and 50 characters")
    private String make;

    @NotBlank(message = "Model is required")
    @Size(min = 1, max = 100, message = "Model must be between 1 and 100 characters")
    private String model;

    @NotNull(message = "Year is required")
    @Min(value = 1900, message = "Year must be after 1900")
    @Max(value = 2030, message = "Year must not exceed 2030")
    private Integer year;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Price cannot exceed 999,999,999.99")
    @Digits(integer = 9, fraction = 2, message = "Price must have at most 9 integer digits and 2 decimal places")
    private BigDecimal price;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    @Pattern(regexp = "^(https?://.*\\.(jpg|jpeg|png|gif|webp))$|^$", message = "Please provide a valid image URL")
    private String imageUrl;

    @Min(value = 0, message = "Mileage cannot be negative")
    @Max(value = 9999999, message = "Mileage cannot exceed 9,999,999")
    private Integer mileage;

    @Size(max = 50, message = "Fuel type must not exceed 50 characters")
    private String fuelType;

    @Size(max = 50, message = "Transmission must not exceed 50 characters")
    private String transmission;

    @Size(max = 50, message = "Color must not exceed 50 characters")
    private String color;

    @Min(value = 1, message = "Number of owners must be at least 1")
    @Max(value = 20, message = "Number of owners cannot exceed 20")
    private Integer numberOfOwners;

    @Size(max = 500, message = "Video URL must not exceed 500 characters")
    private String videoUrl;

    private Boolean accidentHistory;
    private Boolean repaintedParts;
    private Boolean engineIssues;
    private Boolean floodDamage;
    private Boolean insuranceClaims;
    private String variant;
    private String status;
    private String usage;

    private java.util.List<String> images;

    /**
     * List of Temporary File IDs to finalize and attach to this car.
     * Used for new secure upload flow.
     */
    private java.util.List<Long> tempFileIds;

    /**
     * Optional: Reference to CarMaster catalog entry for auto-populating specs.
     * If provided, specs from the catalog can be used as defaults.
     */
    private Long carMasterId;
}
