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
 * User preference entity for storing onboarding vehicle preferences.
 * Stores multi-select choices for vehicle types, budget ranges, and usage
 * purposes.
 */
@Entity
@Table(name = "user_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /**
     * JSON array of selected vehicle types.
     * Example: ["CAR","SUV","ELECTRIC_VEHICLE"]
     */
    @Column(name = "vehicle_types", length = 500)
    private String vehicleTypes;

    /**
     * JSON array of selected budget ranges.
     * Example: ["BELOW_2L","2L_5L"]
     */
    @Column(name = "budget_ranges", length = 500)
    private String budgetRanges;

    /**
     * JSON array of selected usage purposes.
     * Example: ["PERSONAL","DAILY_COMMUTE"]
     */
    @Column(name = "usage_purposes", length = 500)
    private String usagePurposes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
