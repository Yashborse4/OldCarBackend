package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarInspectionDto;
import com.carselling.oldcar.service.car.CarInspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cars/inspections")
@RequiredArgsConstructor
public class CarInspectionController {
    
    private final CarInspectionService carInspectionService;
    
    @PostMapping("/schedule/{carId}")
    public ResponseEntity<CarInspectionDto> scheduleInspection(@PathVariable Long carId, @RequestBody CarInspectionDto inspectionDto) {
        return ResponseEntity.ok(carInspectionService.scheduleInspection(carId, inspectionDto));
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CarInspectionDto>> getUserInspections(@PathVariable Long userId) {
        return ResponseEntity.ok(carInspectionService.getUserInspections(userId));
    }
    
    @GetMapping("/car/{carId}")
    public ResponseEntity<List<CarInspectionDto>> getCarInspections(@PathVariable Long carId) {
        return ResponseEntity.ok(carInspectionService.getCarInspections(carId));
    }
    
    @PutMapping("/complete/{inspectionId}")
    public ResponseEntity<CarInspectionDto> completeInspection(@PathVariable Long inspectionId, @RequestBody CarInspectionDto inspectionDto) {
        return ResponseEntity.ok(carInspectionService.completeInspection(inspectionId, inspectionDto));
    }
}
