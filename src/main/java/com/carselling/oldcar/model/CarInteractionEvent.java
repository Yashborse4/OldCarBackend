package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for tracking car interaction events.
 * Every car interaction emits an event for analytics.
 */
@Entity
@Table(name = "car_interaction_events", indexes = {
        @Index(name = "idx_car_event_car", columnList = "car_id"),
        @Index(name = "idx_car_event_user", columnList = "user_id"),
        @Index(name = "idx_car_event_type", columnList = "event_type"),
        @Index(name = "idx_car_event_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarInteractionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // Can be null for anonymous users

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    @Column(name = "session_id", length = 100)
    private String sessionId; // For tracking anonymous sessions

    @Column(name = "device_info", length = 200)
    private String deviceInfo;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "referrer", length = 500)
    private String referrer;

    @Column(name = "metadata", length = 1000)
    private String metadata; // JSON for additional event-specific data

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Event types for car interactions
     */
    public enum EventType {
        CAR_VIEW("Car Detail Viewed"),
        CONTACT_CLICK("Contact Seller Clicked"),
        SAVE("Car Bookmarked"),
        UNSAVE("Car Unbookmarked"),
        SHARE("Car Shared"),
        CHAT_OPEN("Chat Started"),
        CALL_CLICK("Call Button Clicked"),
        WHATSAPP_CLICK("WhatsApp Button Clicked"),
        IMAGE_VIEW("Image Gallery Viewed"),
        COMPARE_ADD("Added to Compare"),
        TEST_DRIVE_REQUEST("Test Drive Requested");

        private final String displayName;

        EventType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
