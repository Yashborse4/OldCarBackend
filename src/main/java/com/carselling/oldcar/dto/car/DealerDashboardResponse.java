package com.carselling.oldcar.dto.car;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerDashboardResponse {
    private long totalViews;
    private long totalUniqueVisitors;
    private long totalCarsAdded;
    private long activeCars;
    private long contactRequestsReceived;
}

