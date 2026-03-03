package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarFinanceDto;
import com.carselling.oldcar.service.car.CarFinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cars/finance")
@RequiredArgsConstructor
public class CarFinanceController {
    
    private final CarFinanceService carFinanceService;
    
    @PostMapping("/calculate/{carId}")
    public ResponseEntity<CarFinanceDto> calculateFinance(@PathVariable Long carId, @RequestBody CarFinanceDto financeDto) {
        return ResponseEntity.ok(carFinanceService.calculateFinance(carId, financeDto));
    }
    
    @GetMapping("/options/{carId}")
    public ResponseEntity<CarFinanceDto> getFinanceOptions(@PathVariable Long carId) {
        return ResponseEntity.ok(carFinanceService.getFinanceOptions(carId));
    }
    
    @PostMapping("/apply/{carId}")
    public ResponseEntity<CarFinanceDto> applyForFinance(@PathVariable Long carId, @RequestBody CarFinanceDto financeDto) {
        return ResponseEntity.ok(carFinanceService.applyForFinance(carId, financeDto));
    }
}
