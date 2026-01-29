package com.carselling.oldcar.dto.graphql;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.carselling.oldcar.dto.user.UserSummary;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CarGraphQLDto {
    private String id;
    private String make;
    private String model;
    private int year;
    private BigDecimal price;
    private String condition;
    private String description;
    private String imageUrl;
    private List<String> images;
    private String videoUrl;
    private String status;
    private String transmission;
    private Integer mileage;
    private String fuelType;
    private UserSummary owner;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
