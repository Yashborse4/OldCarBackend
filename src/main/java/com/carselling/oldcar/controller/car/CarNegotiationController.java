package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarNegotiationDto;
import com.carselling.oldcar.service.car.CarNegotiationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cars/negotiations")
@RequiredArgsConstructor
public class CarNegotiationController {
    
    private final CarNegotiationService carNegotiationService;
    
    @PostMapping("/start/{carId}")
    public ResponseEntity<CarNegotiationDto> startNegotiation(@PathVariable Long carId, @RequestBody CarNegotiationDto negotiationDto) {
        return ResponseEntity.ok(carNegotiationService.startNegotiation(carId, negotiationDto));
    }
    
    @PostMapping("/respond/{negotiationId}")
    public ResponseEntity<CarNegotiationDto> respondToNegotiation(@PathVariable Long negotiationId, @RequestBody CarNegotiationDto negotiationDto) {
        return ResponseEntity.ok(carNegotiationService.respondToNegotiation(negotiationId, negotiationDto));
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CarNegotiationDto>> getUserNegotiations(@PathVariable Long userId) {
        return ResponseEntity.ok(carNegotiationService.getUserNegotiations(userId));
    }
    
    @GetMapping("/car/{carId}")
    public ResponseEntity<List<CarNegotiationDto>> getCarNegotiations(@PathVariable Long carId) {
        return ResponseEntity.ok(carNegotiationService.getCarNegotiations(carId));
    }
    
    @PutMapping("/accept/{negotiationId}")
    public ResponseEntity<CarNegotiationDto> acceptNegotiation(@PathVariable Long negotiationId) {
        return ResponseEntity.ok(carNegotiationService.acceptNegotiation(negotiationId));
    }
    
    @PutMapping("/reject/{negotiationId}")
    public ResponseEntity<CarNegotiationDto> rejectNegotiation(@PathVariable Long negotiationId) {
        return ResponseEntity.ok(carNegotiationService.rejectNegotiation(negotiationId));
    }
}
