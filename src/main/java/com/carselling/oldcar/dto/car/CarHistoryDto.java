package com.carselling.oldcar.dto.car;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CarHistoryDto {
    private Long id;
    private Long carId;
    private Long userId;
    private String eventType;
    private LocalDateTime eventDate;
    private String description;
    private Integer mileage;
    private BigDecimal cost;
    private String location;
    private LocalDateTime createdAt;
}
