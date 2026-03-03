package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarAlertDto;
import com.carselling.oldcar.service.car.CarAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cars/alerts")
@RequiredArgsConstructor
public class CarAlertController {
    
    private final CarAlertService carAlertService;
    
    @PostMapping("/create")
    public ResponseEntity<CarAlertDto> createAlert(@RequestBody CarAlertDto alertDto) {
        return ResponseEntity.ok(carAlertService.createAlert(alertDto));
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CarAlertDto>> getUserAlerts(@PathVariable Long userId) {
        return ResponseEntity.ok(carAlertService.getUserAlerts(userId));
    }
    
    @PutMapping("/update/{alertId}")
    public ResponseEntity<CarAlertDto> updateAlert(@PathVariable Long alertId, @RequestBody CarAlertDto alertDto) {
        return ResponseEntity.ok(carAlertService.updateAlert(alertId, alertDto));
    }
    
    @DeleteMapping("/delete/{alertId}")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long alertId) {
        carAlertService.deleteAlert(alertId);
        return ResponseEntity.ok().build();
    }
}
