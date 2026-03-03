package com.carselling.oldcar.dto.car;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CarNegotiationDto {
    private Long id;
    private Long carId;
    private String carMake;
    private String carModel;
    private Long buyerId;
    private Long sellerId;
    private Double initialPrice;
    private Double offeredPrice;
    private Double counterOffer;
    private Double finalPrice;
    private String status;
    private String message;
    private String responseMessage;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
    private LocalDateTime completedAt;
}
