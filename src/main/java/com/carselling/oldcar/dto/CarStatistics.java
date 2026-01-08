package com.carselling.oldcar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarStatistics {

    private long totalCars;
    private long activeCars;
    private long soldCars;
    private long featuredCars;
    private long inactiveCars;
    private long newCarsLast7Days;
    private LocalDateTime lastUpdated;
}

