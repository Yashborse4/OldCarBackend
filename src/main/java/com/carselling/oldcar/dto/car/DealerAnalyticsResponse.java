package com.carselling.oldcar.dto.car;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerAnalyticsResponse {
    private long totalViews;
    private long totalInquiries;
    private long totalShares;
    private long totalVehicles;
    private double avgDaysOnMarket;

    private List<MonthlyStat> monthlyStats;
    private List<LocationStat> locationStats;
    private List<CarResponse> topPerformers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyStat {
        private String month;
        private long views;
        private long inquiries;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationStat {
        private String location;
        private long count;
    }
}
