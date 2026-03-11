package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.service.car.CarMasterService;
import com.carselling.oldcar.dto.common.ApiResponse;
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
    // TODO(SeniorEng): Security & Reliability - Add rate limiting and @Cacheable,
    // as autocomplete suggestions get hit on every keystroke.
    public ResponseEntity<ApiResponse<List<String>>> getModelSuggestions(
            @RequestParam String make,
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(ApiResponse.success("Suggestions retrieved successfully",
                carMasterService.getModelSuggestions(make, query)));
    }
}
