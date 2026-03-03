package com.carselling.oldcar.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "car_finance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarFinance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id", nullable = false)
    private Car car;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "down_payment")
    private Double downPayment;
    
    @Column(name = "loan_amount")
    private Double loanAmount;
    
    @Column(name = "interest_rate")
    private Double interestRate;
    
    @Column(name = "loan_term")
    private Integer loanTerm;
    
    @Column(name = "monthly_payment")
    private Double monthlyPayment;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "applied_date")
    private LocalDateTime appliedDate;
}
