package com.carselling.oldcar.dto.car;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Per-vehicle analytics response DTO.
 * All metrics are event-sourced from {@code car_interaction_events},
 * ensuring owner views are excluded.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarAnalyticsResponse {
    private String vehicleId;

    // ── Core Counters ──────────────────────────────────
    private Long views;
    private Long uniqueViewers;
    private Long inquiries;
    private Long shares;
    private Long saves;
    private Long coListings;

    // ── Business KPIs ──────────────────────────────────
    /** Views → Inquiry conversion as percentage (0.0–100.0). */
    private Double conversionRate;

    /** Views → Contact (chat/call/whatsapp) rate as percentage. */
    private Double contactRate;

    /**
     * Composite engagement score (0–100) derived from views,
     * saves, shares, and contacts relative to market averages.
     */
    private Integer engagementScore;

    // ── Metadata ───────────────────────────────────────
    private Integer avgTimeOnMarket;
    private LocalDateTime lastActivity;
    private List<String> topLocations;
    private Integer dealerInterest;

    // ── Breakdowns ─────────────────────────────────────
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
        private Long chatInquiries;
        private Long callInquiries;
        private Long whatsappInquiries;
        private Long totalContacts;
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
