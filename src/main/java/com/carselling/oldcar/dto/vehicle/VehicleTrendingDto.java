package com.carselling.oldcar.dto.vehicle;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for trending vehicles
 */
@Data
@Builder
public class VehicleTrendingDto {
    
    private VehicleSummaryDto vehicle;
    private Integer viewCount;
    private Double trendingScore;
    
    public VehicleTrendingDto(VehicleSummaryDto vehicle, Integer viewCount, Double trendingScore) {
        this.vehicle = vehicle;
        this.viewCount = viewCount;
        this.trendingScore = trendingScore;
    }
}
