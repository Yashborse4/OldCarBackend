package com.carselling.oldcar.dto.car;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Car Analytics Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarAnalyticsResponse {
    private String vehicleId;
    private Long views;
    private Long inquiries;
    private Long shares;
    private Long coListings;
    private Integer avgTimeOnMarket;
    private LocalDateTime lastActivity;
    private List<String> topLocations;
    private Integer dealerInterest; // percentage 0-100
    private ViewsBreakdown viewsBreakdown;
    private InquiriesBreakdown inquiriesBreakdown;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViewsBreakdown {
        private Long dailyViews;
        private Long weeklyViews;
        private Long monthlyViews;
        private List<DailyView> viewsTimeline;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InquiriesBreakdown {
        private Long phoneInquiries;
        private Long chatInquiries;
        private Long emailInquiries;
        private List<String> commonQuestions;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyView {
        private String date;
        private Long views;
    }
}
