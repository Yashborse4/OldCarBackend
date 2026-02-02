package com.carselling.oldcar.document;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VehicleSearchDocument {

    /*
     * ============================================================
     * Core Identifiers
     * ============================================================
     */

    private String id; // ES document ID
    private Long carId; // Reference to DB car table
    private Long dealerId; // Reference to dealer/user table

    /*
     * ============================================================
     * Primary Searchable Text Fields
     * ============================================================
     */

    private String brand;
    private String model;
    private String variant;
    private String city;
    private String fuelType;
    private String transmission;

    /*
     * ============================================================
     * Normalized Fields (for partial & typo tolerance)
     * ============================================================
     */

    private String normalizedBrand; // e.g. "hyundai"
    private String normalizedModel; // e.g. "i20"
    private String normalizedVariant;
    private String normalizedCity;

    /*
     * ============================================================
     * Numeric & Range Filter Fields
     * ============================================================
     */

    private Integer year;
    private Double price;
    private Integer mileage;

    /*
     * ============================================================
     * Dealer & Approval Controls (BUSINESS RULES)
     * ============================================================
     */

    private boolean dealerVerified; // Dealer approved by admin
    private boolean carApproved; // Car approved by admin
    private boolean active; // Soft delete support

    /*
     * ============================================================
     * Ranking & Boosting Fields
     * ============================================================
     */

    private Instant createdAt; // Boost recent cars
    private Integer viewCount; // Popularity boost
    private Integer leadCount; // Engagement score

    /*
     * ============================================================
     * Geo Location (Future Proofing)
     * ============================================================
     */

    private String location; // "lat,lon"

    /*
     * ============================================================
     * Media (Minimal – NOT full assets)
     * ============================================================
     */

    private String thumbnailImageUrl;

    /*
     * ============================================================
     * Tags / Flags
     * ============================================================
     */

    private List<String> highlights; // e.g. ["single-owner", "verified"]
}
