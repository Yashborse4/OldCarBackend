package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.car.CarFinanceDto;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarFinance;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarFinanceRepository;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CarFinanceService {
    
    private final CarFinanceRepository carFinanceRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    
    public CarFinanceDto calculateFinance(Long carId, CarFinanceDto financeDto) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));
        
        double monthlyPayment = calculateMonthlyPayment(
                car.getPrice(), 
                financeDto.getDownPayment(), 
                financeDto.getInterestRate(), 
                financeDto.getLoanTerm()
        );
        
        return CarFinanceDto.builder()
                .carId(carId)
                .carPrice(car.getPrice())
                .downPayment(financeDto.getDownPayment())
                .loanAmount(car.getPrice() - financeDto.getDownPayment())
                .interestRate(financeDto.getInterestRate())
                .loanTerm(financeDto.getLoanTerm())
                .monthlyPayment(monthlyPayment)
                .totalPayment(monthlyPayment * financeDto.getLoanTerm())
                .totalInterest((monthlyPayment * financeDto.getLoanTerm()) - (car.getPrice() - financeDto.getDownPayment()))
                .build();
    }
    
    public CarFinanceDto getFinanceOptions(Long carId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));
        
        return CarFinanceDto.builder()
                .carId(carId)
                .carPrice(car.getPrice())
                .downPayment(car.getPrice() * 0.2)
                .loanAmount(car.getPrice() * 0.8)
                .interestRate(6.5)
                .loanTerm(60)
                .monthlyPayment(calculateMonthlyPayment(car.getPrice(), car.getPrice() * 0.2, 6.5, 60))
                .build();
    }
    
    public CarFinanceDto applyForFinance(Long carId, CarFinanceDto financeDto) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));
        
        User currentUser = getCurrentUser();
        
        CarFinance finance = CarFinance.builder()
                .car(car)
                .user(currentUser)
                .downPayment(financeDto.getDownPayment())
                .loanAmount(financeDto.getLoanAmount())
                .interestRate(financeDto.getInterestRate())
                .loanTerm(financeDto.getLoanTerm())
                .monthlyPayment(financeDto.getMonthlyPayment())
                .status("PENDING")
                .appliedDate(LocalDateTime.now())
                .build();
        
        carFinanceRepository.save(finance);
        
        return financeDto;
    }
    
    private double calculateMonthlyPayment(double carPrice, double downPayment, double interestRate, int loanTerm) {
        double loanAmount = carPrice - downPayment;
        double monthlyRate = interestRate / 100 / 12;
        
        if (monthlyRate == 0) {
            return loanAmount / loanTerm;
        }
        
        double monthlyPayment = loanAmount * (monthlyRate * Math.pow(1 + monthlyRate, loanTerm)) / 
                               (Math.pow(1 + monthlyRate, loanTerm) - 1);
        
        return BigDecimal.valueOf(monthlyPayment).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
    
    private User getCurrentUser() {
        return userRepository.findById(1L).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
