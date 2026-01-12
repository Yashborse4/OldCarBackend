package com.carselling.oldcar.controller;

import com.carselling.oldcar.service.CarMasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cars/master")
@RequiredArgsConstructor
@CrossOrigin
public class CarMasterController {

    private final CarMasterService carMasterService;

    @GetMapping("/suggestions")
    public ResponseEntity<List<String>> getModelSuggestions(
            @RequestParam String make,
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(carMasterService.getModelSuggestions(make, query));
    }
}
