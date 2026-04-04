package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity to represent a formal enquiry or lead from a buyer.
 */
@Entity
@Table(name = "enquiries", indexes = {
        @Index(name = "idx_enquiry_car", columnList = "car_id"),
        @Index(name = "idx_enquiry_user", columnList = "user_id"),
        @Index(name = "idx_enquiry_status", columnList = "status"),
        @Index(name = "idx_enquiry_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "enquiry_type", nullable = false, length = 30)
    private EnquiryType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EnquiryStatus status;

    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "preferred_time_slot", length = 50)
    private String preferredTimeSlot; // e.g., "MORNING", "AFTER_6PM"

    @Column(name = "contact_number", length = 15)
    private String contactNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum EnquiryType {
        CALLBACK_REQUEST,
        TEST_DRIVE_REQUEST,
        GENERAL_INQUIRY,
        PRICE_NEGOTIATION
    }

    public enum EnquiryStatus {
        NEW,
        CONTACTED,
        FOLLOW_UP,
        VISIT_SCHEDULED,
        SOLD,
        LOST
    }
}
