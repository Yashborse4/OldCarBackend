package com.carselling.oldcar.dto.vehicle;

import lombok.Builder;
import lombok.Data;

/**
 * DTO for vehicle recommendations
 */
@Data
@Builder
public class VehicleRecommendationDto {
    
    private VehicleSummaryDto vehicle;
    private Double recommendationScore;
    private String reason;
    
    public VehicleRecommendationDto(VehicleSummaryDto vehicle, Double recommendationScore, String reason) {
        this.vehicle = vehicle;
        this.recommendationScore = recommendationScore;
        this.reason = reason;
    }
}
