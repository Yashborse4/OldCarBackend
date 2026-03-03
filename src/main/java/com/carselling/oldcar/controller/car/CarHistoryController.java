package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarHistoryDto;
import com.carselling.oldcar.service.car.CarHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cars/history")
@RequiredArgsConstructor
public class CarHistoryController {
    
    private final CarHistoryService carHistoryService;
    
    @GetMapping("/car/{carId}")
    public ResponseEntity<List<CarHistoryDto>> getCarHistory(@PathVariable Long carId) {
        return ResponseEntity.ok(carHistoryService.getCarHistory(carId));
    }
    
    @PostMapping("/add/{carId}")
    public ResponseEntity<CarHistoryDto> addHistoryRecord(@PathVariable Long carId, @RequestBody CarHistoryDto historyDto) {
        return ResponseEntity.ok(carHistoryService.addHistoryRecord(carId, historyDto));
    }
    
    @GetMapping("/ownership/{carId}")
    public ResponseEntity<List<CarHistoryDto>> getOwnershipHistory(@PathVariable Long carId) {
        return ResponseEntity.ok(carHistoryService.getOwnershipHistory(carId));
    }
    
    @GetMapping("/maintenance/{carId}")
    public ResponseEntity<List<CarHistoryDto>> getMaintenanceHistory(@PathVariable Long carId) {
        return ResponseEntity.ok(carHistoryService.getMaintenanceHistory(carId));
    }
    
    @GetMapping("/accidents/{carId}")
    public ResponseEntity<List<CarHistoryDto>> getAccidentHistory(@PathVariable Long carId) {
        return ResponseEntity.ok(carHistoryService.getAccidentHistory(carId));
    }
}
