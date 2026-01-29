package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.vehicle.VehiclePriceAnalysisDto;
import com.carselling.oldcar.dto.vehicle.VehiclePriceAnalysisRequest;
import com.carselling.oldcar.service.car.CarPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for pricing analysis.
 */
@RestController
@RequestMapping("/api/prices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pricing Analysis", description = "Vehicle valuation and market analysis")
public class CarPriceController {

    private final CarPriceService carPriceService;

    /**
     * Analyze vehicle price
     */
    @PostMapping("/analyze")
    @Operation(summary = "Analyze vehicle price", description = "Get market analysis and price recommendation")
    public ResponseEntity<ApiResponse<VehiclePriceAnalysisDto>> analyzePrice(
            @Valid @RequestBody VehiclePriceAnalysisRequest request) {

        VehiclePriceAnalysisDto analysis = carPriceService.analyzePrice(request);

        return ResponseEntity.ok(ApiResponse.success("Price analysis completed", analysis));
    }
}
