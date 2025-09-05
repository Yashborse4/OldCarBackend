package com.carselling.oldcar.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Elasticsearch document for vehicle search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "vehicles")
@Setting(settingPath = "/elasticsearch/vehicle-settings.json")
@Mapping(mappingPath = "/elasticsearch/vehicle-mapping.json")
public class VehicleSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long vehicleId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Keyword)
    private String make;

    @Field(type = FieldType.Keyword)
    private String model;

    @Field(type = FieldType.Integer)
    private Integer year;

    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Field(type = FieldType.Integer)
    private Integer mileage;

    @Field(type = FieldType.Keyword)
    private String fuelType;

    @Field(type = FieldType.Keyword)
    private String transmission;

    @Field(type = FieldType.Keyword)
    private String bodyType;

    @Field(type = FieldType.Keyword)
    private String condition;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String color;

    @Field(type = FieldType.Text)
    private String location;

    @Field(type = FieldType.Keyword)
    private String city;

    @Field(type = FieldType.Keyword)
    private String state;

    @Field(type = FieldType.Keyword)
    private String country;

    // Geographic location for geo-spatial search
    @GeoPointField
    private String geoLocation; // Format: "lat,lon"

    @Field(type = FieldType.Nested)
    private List<String> features;

    @Field(type = FieldType.Nested)
    private List<String> images;

    @Field(type = FieldType.Nested)
    private List<String> tags;

    // Owner information
    @Field(type = FieldType.Long)
    private Long ownerId;

    @Field(type = FieldType.Keyword)
    private String ownerType; // INDIVIDUAL, DEALER

    @Field(type = FieldType.Text)
    private String ownerName;

    // Vehicle specifications
    @Field(type = FieldType.Keyword)
    private String engineSize;

    @Field(type = FieldType.Integer)
    private Integer numberOfDoors;

    @Field(type = FieldType.Integer)
    private Integer numberOfSeats;

    @Field(type = FieldType.Keyword)
    private String driveType; // FWD, RWD, AWD, 4WD

    // Market analysis fields
    @Field(type = FieldType.Double)
    private BigDecimal marketValue;

    @Field(type = FieldType.Double)
    private BigDecimal priceScore; // Price competitiveness score

    @Field(type = FieldType.Integer)
    private Integer popularity; // Number of views/likes

    @Field(type = FieldType.Integer)
    private Integer inquiryCount;

    // Status and metadata
    @Field(type = FieldType.Keyword)
    private String status; // ACTIVE, SOLD, PENDING

    @Field(type = FieldType.Boolean)
    private Boolean isVerified;

    @Field(type = FieldType.Boolean)
    private Boolean isFeatured;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime updatedAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime lastViewedAt;

    // Search boost fields
    @Field(type = FieldType.Double)
    private Double searchBoost; // For ranking purposes

    @Field(type = FieldType.Keyword)
    private String categoryPath; // For faceted search

    // Additional search fields
    @Field(type = FieldType.Text, analyzer = "keyword")
    private String searchableText; // Combined searchable content

    @Field(type = FieldType.Completion)
    private CompletionSuggestion suggest;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompletionSuggestion {
        private String[] input;
        private Integer weight;
        private String[] contexts;
    }

    /**
     * Create search suggestions for auto-complete
     */
    public static CompletionSuggestion createSuggestion(String make, String model, Integer year) {
        return CompletionSuggestion.builder()
                .input(new String[]{
                    make,
                    model,
                    make + " " + model,
                    year + " " + make,
                    year + " " + model,
                    year + " " + make + " " + model
                })
                .weight(1)
                .build();
    }

    /**
     * Build searchable text from all relevant fields
     */
    public void buildSearchableText() {
        StringBuilder sb = new StringBuilder();
        
        if (make != null) sb.append(make).append(" ");
        if (model != null) sb.append(model).append(" ");
        if (year != null) sb.append(year).append(" ");
        if (bodyType != null) sb.append(bodyType).append(" ");
        if (fuelType != null) sb.append(fuelType).append(" ");
        if (transmission != null) sb.append(transmission).append(" ");
        if (color != null) sb.append(color).append(" ");
        if (location != null) sb.append(location).append(" ");
        if (description != null) sb.append(description).append(" ");
        if (features != null) {
            features.forEach(feature -> sb.append(feature).append(" "));
        }
        if (tags != null) {
            tags.forEach(tag -> sb.append(tag).append(" "));
        }
        
        this.searchableText = sb.toString().trim().toLowerCase();
    }

    /**
     * Calculate search boost based on vehicle attributes
     */
    public void calculateSearchBoost() {
        double boost = 1.0;
        
        // Boost verified vehicles
        if (Boolean.TRUE.equals(isVerified)) {
            boost += 0.5;
        }
        
        // Boost featured vehicles
        if (Boolean.TRUE.equals(isFeatured)) {
            boost += 0.3;
        }
        
        // Boost based on popularity
        if (popularity != null && popularity > 0) {
            boost += Math.min(popularity * 0.01, 1.0);
        }
        
        // Boost based on inquiry count
        if (inquiryCount != null && inquiryCount > 0) {
            boost += Math.min(inquiryCount * 0.05, 0.5);
        }
        
        // Boost newer listings
        if (createdAt != null) {
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            if (createdAt.isAfter(thirtyDaysAgo)) {
                boost += 0.2;
            }
        }
        
        this.searchBoost = boost;
    }
}
