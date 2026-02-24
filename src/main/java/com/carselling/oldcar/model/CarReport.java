package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "car_reports", indexes = {
    @Index(name = "idx_car_report_car_id", columnList = "reported_car_id"),
    @Index(name = "idx_car_report_status", columnList = "status"),
    @Index(name = "idx_car_report_created_at", columnList = "created_at"),
    @Index(name = "idx_car_report_reporter", columnList = "reporter_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_car_id", nullable = false)
    private Car reportedCar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private CarReportReason reason;

    @Column(columnDefinition = "TEXT")
    private String additionalComments;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderator_id")
    private User moderator;

    @Column(columnDefinition = "TEXT")
    private String moderatorNotes;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(columnDefinition = "TEXT")
    private String carSnapshot;

    public enum CarReportReason {
        FAKE_LISTING,
        WRONG_INFORMATION,
        STOLEN_VEHICLE,
        PRICING_SCAM,
        INAPPROPRIATE_CONTENT,
        SPAM,
        DUPLICATE_LISTING,
        OTHER
    }
}