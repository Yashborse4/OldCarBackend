package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarComparisonDto;
import com.carselling.oldcar.service.car.CarComparisonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cars/comparison")
@RequiredArgsConstructor
public class CarComparisonController {
    
    private final CarComparisonService carComparisonService;
    
    @PostMapping("/compare")
    public ResponseEntity<List<CarComparisonDto>> compareCars(@RequestBody List<Long> carIds) {
        return ResponseEntity.ok(carComparisonService.compareCars(carIds));
    }
    
    @GetMapping("/similar/{carId}")
    public ResponseEntity<List<CarComparisonDto>> getSimilarCars(@PathVariable Long carId) {
        return ResponseEntity.ok(carComparisonService.getSimilarCars(carId));
    }
}
