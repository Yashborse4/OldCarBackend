package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Stores aggregated behavioral preference scores for users based on their
 * interactions.
 */
@Entity
@Table(name = "user_preference_scores", indexes = {
        @Index(name = "idx_ups_user", columnList = "user_id"),
        @Index(name = "idx_ups_updated", columnList = "updated_at")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "attribute_type", "attribute_value" })
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferenceScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_type", nullable = false, length = 30)
    private AttributeType attributeType;

    @Column(name = "attribute_value", nullable = false, length = 100)
    private String attributeValue;

    @Column(name = "score", nullable = false)
    @Builder.Default
    private Double score = 0.0;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Explicitly add a created_at field for tracking when it was first observed
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum AttributeType {
        CATEGORY, // e.g., Sedan, SUV
        MAKE, // e.g., Toyota, BMW
        FUEL_TYPE, // e.g., PETROL, DIESEL, ELECTRIC
        PRICE_BUCKET, // e.g., BELOW_5L, 5L_10L, 10L_20L, ABOVE_20L
        BODY_TYPE
    }
}
