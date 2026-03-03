package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "car_test_drives")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarTestDrive {
    
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
    
    @Column(name = "confirmed_date")
    private LocalDateTime confirmedDate;
    
    @Column(name = "cancelled_date")
    private LocalDateTime cancelledDate;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "contact_number")
    private String contactNumber;
    
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;
}
