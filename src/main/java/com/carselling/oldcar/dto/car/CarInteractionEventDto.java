package com.carselling.oldcar.dto.car;

import com.carselling.oldcar.model.CarInteractionEvent.EventType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for tracking car interaction events
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarInteractionEventDto {

    @NotNull(message = "Car ID is required")
    private Long carId;

    @NotNull(message = "Event type is required")
    private String eventType;

    private String sessionId;
    private String deviceInfo;
    private String referrer;
    private String metadata;

    /**
     * Convert eventType string to enum
     */
    public EventType getEventTypeEnum() {
        try {
            return EventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
