package com.carselling.oldcar.document;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;

/**
 * Elasticsearch Search Document
 *
 * This document is optimized ONLY for search.
 * It must NOT be used as:
 * - JPA Entity
 * - API Request/Response DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor // Replaces explicit empty constructor
@Document(indexName = "vehicle_search_v1")
public class VehicleSearchDocument {

    /*
     * ============================================================
     * Core Identifiers
     * ============================================================
     */

    @Id
    private String id; // ES document ID
    private Long carId; // Reference to DB car table
    private Long dealerId; // Reference to dealer/user table

    /*
     * ============================================================
     * Primary Searchable Text Fields
     * ============================================================
     */

    @Field(type = FieldType.Text, analyzer = "standard")
    private String brand;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String model;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String variant;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String city;

    @Field(type = FieldType.Keyword)
    private String fuelType;

    @Field(type = FieldType.Keyword)
    private String transmission;

    /*
     * ============================================================
     * Normalized Fields (for partial & typo tolerance)
     * ============================================================
     */

    @Field(type = FieldType.Keyword)
    private String normalizedBrand; // e.g. "hyundai"

    @Field(type = FieldType.Keyword)
    private String normalizedModel; // e.g. "i20"

    @Field(type = FieldType.Keyword)
    private String normalizedVariant;

    @Field(type = FieldType.Keyword)
    private String normalizedCity;

    /*
     * ============================================================
     * Numeric & Range Filter Fields
     * ============================================================
     */

    @Field(type = FieldType.Integer)
    private Integer year;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Integer)
    private Integer mileage;

    /*
     * ============================================================
     * Dealer & Approval Controls (BUSINESS RULES)
     * ============================================================
     */

    @Field(type = FieldType.Boolean)
    private boolean dealerVerified; // Dealer approved by admin

    @Field(type = FieldType.Boolean)
    private boolean carApproved; // Car approved by admin

    @Field(type = FieldType.Boolean)
    private boolean active; // Soft delete support

    /*
     * ============================================================
     * Ranking & Boosting Fields
     * ============================================================
     */

    @Field(type = FieldType.Date)
    private Instant createdAt; // Boost recent cars

    @Field(type = FieldType.Integer)
    private Integer viewCount; // Popularity boost

    @Field(type = FieldType.Integer)
    private Integer leadCount; // Engagement score

    /*
     * ============================================================
     * Geo Location (Future Proofing)
     * ============================================================
     */

    @GeoPointField
    private String location; // "lat,lon"

    /*
     * ============================================================
     * Media (Minimal – NOT full assets)
     * ============================================================
     */

    @Field(type = FieldType.Keyword)
    private String thumbnailImageUrl;

    /*
     * ============================================================
     * Tags / Flags
     * ============================================================
     */

    @Field(type = FieldType.Keyword)
    private List<String> highlights; // e.g. ["single-owner", "verified"]
}
