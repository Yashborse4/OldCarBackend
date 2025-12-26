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
        private String city;
        private Integer minYear;
        private Integer maxYear;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private Boolean verifiedDealer;
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
        private String sellerType;
        private LocalDateTime createdAt;

        public static CarSearchHitDto fromDocument(VehicleSearchDocument doc) {
            return CarSearchHitDto.builder()
                    .id(doc.getId())
                    .brand(doc.getBrand())
                    .model(doc.getModel())
                    .variant(doc.getVariant())
                    .year(doc.getYear())
                    .price(doc.getPrice())
                    .mileage(doc.getMileage())
                    .city(doc.getCity())
                    .fuelType(doc.getFuelType())
                    .transmission(doc.getTransmission())
                    .verifiedDealer(doc.getVerifiedDealer())
                    .thumbnailUrl(doc.getThumbnailUrl())
                    .sellerType(doc.getSellerType())
                    .createdAt(doc.getCreatedAt())
                    .build();
        }
    }
}
