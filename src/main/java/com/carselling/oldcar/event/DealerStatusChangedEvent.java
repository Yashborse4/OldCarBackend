package com.carselling.oldcar.event;

import com.carselling.oldcar.model.DealerStatus;
import com.carselling.oldcar.model.User;
import org.springframework.context.ApplicationEvent;

/**
 * Event published when a dealer's status changes.
 * Replaces the simpler DealerVerifiedEvent to support full status workflow.
 */
public class DealerStatusChangedEvent extends ApplicationEvent {

    private final User dealer;
    private final DealerStatus previousStatus;
    private final DealerStatus newStatus;
    private final String reason;

    public DealerStatusChangedEvent(Object source, User dealer, DealerStatus previousStatus,
            DealerStatus newStatus, String reason) {
        super(source);
        this.dealer = dealer;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
    }

    public User getDealer() {
        return dealer;
    }

    public DealerStatus getPreviousStatus() {
        return previousStatus;
    }

    public DealerStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    /**
     * Check if this was a verification (approval) action.
     */
    public boolean isVerification() {
        return newStatus == DealerStatus.VERIFIED;
    }

    /**
     * Check if this was a suspension action.
     */
    public boolean isSuspension() {
        return newStatus == DealerStatus.SUSPENDED;
    }

    /**
     * Check if this was a rejection action.
     */
    public boolean isRejection() {
        return newStatus == DealerStatus.REJECTED;
    }
}
