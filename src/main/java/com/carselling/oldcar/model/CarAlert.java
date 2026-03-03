package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "car_alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarAlert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "make")
    private String make;
    
    @Column(name = "model")
    private String model;
    
    @Column(name = "min_year")
    private Integer minYear;
    
    @Column(name = "max_year")
    private Integer maxYear;
    
    @Column(name = "min_price")
    private Double minPrice;
    
    @Column(name = "max_price")
    private Double maxPrice;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
