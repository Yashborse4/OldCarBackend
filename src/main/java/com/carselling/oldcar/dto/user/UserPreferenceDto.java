package com.carselling.oldcar.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for user preference data transfer.
 * Used for both request and response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferenceDto {

    /**
     * Selected vehicle types (multi-select).
     * Values: CAR, BIKE, TRUCK, SUV, VAN, ELECTRIC_VEHICLE, COMMERCIAL_VEHICLE,
     * LUXURY_CARS
     */
    private List<String> vehicleTypes;

    /**
     * Selected budget ranges (multi-select).
     * Values: BELOW_2L, 2L_5L, 5L_10L, ABOVE_10L
     */
    private List<String> budgetRanges;

    /**
     * Selected usage purposes (multi-select).
     * Values: PERSONAL, BUSINESS, RESALE, RENTAL, DAILY_COMMUTE
     */
    private List<String> usagePurposes;
}
