package com.carselling.oldcar.dto.car;

import com.carselling.oldcar.document.VehicleSearchDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lightweight DTOs for car search results and criteria.
 */
public class CarSearchDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CarSearchRequest {
        private String keyword;
        private java.util.List<String> brands;
        private java.util.List<String> models;
        private String variant;
        private java.util.List<String> fuelTypes;
        private java.util.List<String> transmissions;
        private java.util.List<String> cities;
        private Integer minYear;
        private Integer maxYear;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private Long minMileage;
        private Long maxMileage;
        private String usage; // corresponds to 'condition' from frontend
        private String category; // corresponds to 'bodyType' from frontend
        private Integer numberOfOwners;
        private Boolean verifiedDealer;
        private Double latitude;
        private Double longitude;
        private Double radiusKm;

        // Pagination & Sorting
        @Builder.Default
        private Integer page = 0;
        @Builder.Default
        private Integer size = 20;
        @Builder.Default
        private String sort = "relevance";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CarSearchHitDto {
        private String id;
        private String brand;
        private String model;
        private String variant;
        private Integer year;
        private BigDecimal price;
        private Integer mileage;
        private String city;
        private String fuelType;
        private String transmission;
        private Boolean verifiedDealer;
        private String thumbnailUrl;
        private String primaryImage;
        private String sellerType;
        private String ownerName;
        private String condition;
        private Integer views;
        private LocalDateTime createdAt;
        private String dealerId;
        private Boolean isPromoted;
        private Double distanceKm;
        private String status;
        private Boolean isAvailable;

        public static CarSearchHitDto fromDocument(VehicleSearchDocument doc) {
            return CarSearchHitDto.builder()
                    .id(doc.getId())
                    .brand(doc.getBrand())
                    .model(doc.getModel())
                    .variant(doc.getVariant())
                    .year(doc.getYear())
                    .price(doc.getPrice() != null ? BigDecimal.valueOf(doc.getPrice()) : null)
                    .mileage(doc.getMileage())
                    .city(doc.getCity())
                    .fuelType(doc.getFuelType())
                    .transmission(doc.getTransmission())
                    .verifiedDealer(doc.isDealerVerified())
                    .thumbnailUrl(doc.getThumbnailImageUrl())
                    .primaryImage(doc.getThumbnailImageUrl())
                    // Fields from document
                    .sellerType(doc.isDealerVerified() ? "DEALER" : "USER")
                    .ownerName(null) // Not typically in search document for privacy
                    .condition(doc.getUsage() != null ? doc.getUsage() : "Used")
                    .views(doc.getViewCount())
                    .createdAt(doc.getCreatedAt() != null
                            ? LocalDateTime.ofInstant(doc.getCreatedAt(), java.time.ZoneId.systemDefault())
                            : null)
                    .distanceKm(doc.getDistanceKm())
                    .status(doc.getStatus())
                    .isAvailable(doc.isAvailable())
                    .build();
        }
    }
}
