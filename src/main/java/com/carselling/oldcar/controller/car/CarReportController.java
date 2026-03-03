package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarReportDto;
import com.carselling.oldcar.service.car.CarReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cars/reports")
@RequiredArgsConstructor
public class CarReportController {

    private final CarReportService carReportService;

    @PostMapping("/{carId}")
    public ResponseEntity<Void> submitReport(
            @PathVariable Long carId,
            @RequestBody com.carselling.oldcar.dto.car.CarReportRequest request) {
        // In a real app, reporterId would come from security context
        // Using 1L as placeholder or assuming we implement security retrieval later
        Long reporterId = 1L;
        carReportService.submitReport(carId, reporterId, request.getReason(), request.getComments());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/pending")
    public ResponseEntity<List<com.carselling.oldcar.model.CarReport>> getPendingReports() {
        return ResponseEntity.ok(carReportService.getAllPendingReports());
    }
}
