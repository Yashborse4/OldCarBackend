package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "car_audit_logs", indexes = {
        @Index(name = "idx_audit_car_id", columnList = "car_id"),
        @Index(name = "idx_audit_changed_at", columnList = "changed_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class CarAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "car_id", nullable = false)
    private Long carId;

    @Column(name = "old_price", precision = 11, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "new_price", precision = 11, scale = 2)
    private BigDecimal newPrice;

    @Column(name = "old_mileage")
    private Integer oldMileage;

    @Column(name = "new_mileage")
    private Integer newMileage;

    @Column(name = "changed_by")
    @CreatedBy
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    @CreatedDate
    private LocalDateTime changedAt;
}
