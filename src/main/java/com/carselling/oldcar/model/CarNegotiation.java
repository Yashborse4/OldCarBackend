package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "car_negotiations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarNegotiation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;
    
    @Column(name = "initial_price", nullable = false)
    private Double initialPrice;
    
    @Column(name = "offered_price", nullable = false)
    private Double offeredPrice;
    
    @Column(name = "counter_offer")
    private Double counterOffer;
    
    @Column(name = "final_price")
    private Double finalPrice;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "response_message", columnDefinition = "TEXT")
    private String responseMessage;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
