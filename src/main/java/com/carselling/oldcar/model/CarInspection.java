package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "car_inspections")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarInspection {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "scheduled_date")
    private LocalDateTime scheduledDate;
    
    @Column(name = "completed_date")
    private LocalDateTime completedDate;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "inspector_name")
    private String inspectorName;
    
    @Column(name = "inspection_type")
    private String inspectionType;
    
    @Column(name = "report", columnDefinition = "TEXT")
    private String report;
    
    @Column(name = "score")
    private Integer score;
}
