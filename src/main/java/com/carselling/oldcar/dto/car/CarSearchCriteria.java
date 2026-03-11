package com.carselling.oldcar.dto.car;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Car Search Criteria DTO for advanced filtering
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarSearchCriteria {
    private java.util.List<String> make;
    private java.util.List<String> model;
    private Integer minYear;
    private Integer maxYear;
    private Long minPrice;
    private Long maxPrice;
    private java.util.List<String> location;
    private String condition;
    private String status;
    private Boolean featured;
    private Boolean verifiedDealer;
    private java.util.List<String> fuelType;
    private java.util.List<String> transmission;
    private String variant; // Car variant (e.g., LX, EX, Sport, etc.)
    private java.util.List<String> category;
    private java.util.List<String> registrationType;
    private String bodyType;
    private String color;
    private Integer minSeatingCapacity;
    private Integer maxSeatingCapacity;
    private Long minMileage;
    private Long maxMileage;
    private String dealerId;
    private Integer numberOfOwners;
    private String sortBy;
    private String sortDirection;

    /**
     * Optional free-text query for brand/model/city/year search.
     * Mapped from the `query` request parameter.
     */
    private Double latitude;
    private Double longitude;
    private Double radiusKm;

    private String query;
}
