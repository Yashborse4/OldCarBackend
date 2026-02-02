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
@Table(name = "car_master", indexes = {
        @Index(name = "idx_car_master_make", columnList = "make"),
        @Index(name = "idx_car_master_model", columnList = "model")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false, length = 50)
    private String make;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(length = 200)
    private String variant;

    @Column(length = 50)
    private String segment;

    @Column(name = "engine_cc")
    private Integer engineCC;

    @Column(name = "fuel_type", length = 50)
    private String fuelType;

    @Column(length = 50)
    private String transmission;

    @Column(name = "body_type", length = 50)
    private String bodyType;

    @Column(name = "seating_capacity")
    private Integer seatingCapacity;

    @Column(name = "mileage_arai")
    private Double mileageArai;

    @Column(name = "year_start")
    private Integer yearStart;

    @Column(name = "year_end")
    private Integer yearEnd;

    @Column(name = "power_hp")
    private String powerHp;

    @Column(name = "torque_nm")
    private String torqueNm;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
