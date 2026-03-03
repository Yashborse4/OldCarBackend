package com.carselling.oldcar.dto.car;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CarReviewDto {
    private Long id;
    private Long carId;
    private Long userId;
    private String userName;
    private Integer rating;
    private String title;
    private String comment;
    private String pros;
    private String cons;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
