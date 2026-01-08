package com.carselling.oldcar.model;

/**
 * Dealer status states for verification workflow
 * 
 * Status transitions:
 * - New dealer registration → UNVERIFIED
 * - Admin approves → VERIFIED
 * - Admin suspends → SUSPENDED
 * - Admin rejects → REJECTED
 * - SUSPENDED can be restored to VERIFIED
 */
public enum DealerStatus {

    /**
     * Dealer registered but not yet approved by admin.
     * Can operate business (CRUD listings) but listings are not publicly visible.
     */
    UNVERIFIED("Pending Verification"),

    /**
     * Dealer approved by admin.
     * Full access - can operate business and listings are publicly visible.
     */
    VERIFIED("Verified"),

    /**
     * Dealer temporarily blocked by admin.
     * Cannot create new listings, existing listings hidden from public.
     */
    SUSPENDED("Suspended"),

    /**
     * Dealer permanently rejected by admin.
     * Account disabled, cannot operate business.
     */
    REJECTED("Rejected");

    private final String displayName;

    DealerStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if dealer can perform business operations (CRUD on their listings).
     * UNVERIFIED and VERIFIED dealers can operate, SUSPENDED and REJECTED cannot.
     */
    public boolean canOperateBusiness() {
        return this == UNVERIFIED || this == VERIFIED;
    }

    /**
     * Check if dealer's listings should be visible to public/buyers.
     * Only VERIFIED dealers have public visibility.
     */
    public boolean isPubliclyVisible() {
        return this == VERIFIED;
    }

    /**
     * Check if status can be changed to target status by admin.
     */
    public boolean canTransitionTo(DealerStatus target) {
        if (target == null)
            return false;

        return switch (this) {
            case UNVERIFIED -> target == VERIFIED || target == REJECTED;
            case VERIFIED -> target == SUSPENDED || target == REJECTED;
            case SUSPENDED -> target == VERIFIED || target == REJECTED;
            case REJECTED -> false; // REJECTED is final state
        };
    }

    public static DealerStatus fromString(String status) {
        if (status == null) {
            return UNVERIFIED;
        }
        try {
            return DealerStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNVERIFIED;
        }
    }
}
