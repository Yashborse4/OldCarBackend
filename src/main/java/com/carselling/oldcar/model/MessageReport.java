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
@Table(name = "message_reports", indexes = {
    @Index(name = "idx_report_message_id", columnList = "reported_message_id"),
    @Index(name = "idx_report_status", columnList = "status"),
    @Index(name = "idx_report_created_at", columnList = "created_at"),
    @Index(name = "idx_report_reporter", columnList = "reporter_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_message_id", nullable = false)
    private ChatMessage reportedMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ReportReason reason;

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
    private String messageSnapshot;

    public enum ReportReason {
        SPAM,
        HARASSMENT,
        HATE_SPEECH,
        INAPPROPRIATE_CONTENT,
        FALSE_INFORMATION,
        PRIVACY_VIOLATION,
        OTHER
    }


}