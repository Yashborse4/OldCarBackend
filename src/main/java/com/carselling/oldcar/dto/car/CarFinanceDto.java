package com.carselling.oldcar.dto.car;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CarFinanceDto {
    private Long id;
    private Long carId;
    private Double carPrice;
    private Double downPayment;
    private Double loanAmount;
    private Double interestRate;
    private Integer loanTerm;
    private Double monthlyPayment;
    private Double totalPayment;
    private Double totalInterest;
    private String status;
    private LocalDateTime appliedDate;
}
