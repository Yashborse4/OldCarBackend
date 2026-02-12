package com.carselling.oldcar.dto.car;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dealer Dashboard Response DTO — business-grade KPIs.
 * All view/interaction metrics are sourced from the
 * {@code car_interaction_events} table (owner views excluded).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerDashboardResponse {
    /** Total views across all dealer's cars (owner views excluded). */
    private long totalViews;

    /** Unique users who initiated a chat/call/whatsapp inquiry. */
    private long totalUniqueVisitors;

    /** Total cars added (all time). */
    private long totalCarsAdded;

    /** Currently active (non-deleted, non-sold) listings. */
    private long activeCars;

    /** Total contact requests (chat + call + whatsapp). */
    private long contactRequestsReceived;

    /** Views → Contact conversion rate as percentage (0.0–100.0). */
    private double conversionRate;

    /** Total shares across all cars. */
    private long totalShares;

    /** Total saves/bookmarks across all cars. */
    private long totalSaves;
}
