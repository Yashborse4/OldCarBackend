package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarTestDriveDto;
import com.carselling.oldcar.service.car.CarTestDriveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cars/test-drives")
@RequiredArgsConstructor
public class CarTestDriveController {
    
    private final CarTestDriveService carTestDriveService;
    
    @PostMapping("/schedule/{carId}")
    public ResponseEntity<CarTestDriveDto> scheduleTestDrive(@PathVariable Long carId, @RequestBody CarTestDriveDto testDriveDto) {
        return ResponseEntity.ok(carTestDriveService.scheduleTestDrive(carId, testDriveDto));
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CarTestDriveDto>> getUserTestDrives(@PathVariable Long userId) {
        return ResponseEntity.ok(carTestDriveService.getUserTestDrives(userId));
    }
    
    @GetMapping("/car/{carId}")
    public ResponseEntity<List<CarTestDriveDto>> getCarTestDrives(@PathVariable Long carId) {
        return ResponseEntity.ok(carTestDriveService.getCarTestDrives(carId));
    }
    
    @PutMapping("/confirm/{testDriveId}")
    public ResponseEntity<CarTestDriveDto> confirmTestDrive(@PathVariable Long testDriveId) {
        return ResponseEntity.ok(carTestDriveService.confirmTestDrive(testDriveId));
    }
    
    @PutMapping("/cancel/{testDriveId}")
    public ResponseEntity<CarTestDriveDto> cancelTestDrive(@PathVariable Long testDriveId) {
        return ResponseEntity.ok(carTestDriveService.cancelTestDrive(testDriveId));
    }
}
