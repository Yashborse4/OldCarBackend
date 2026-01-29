package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.vehicle.VehiclePriceAnalysisDto;
import com.carselling.oldcar.dto.vehicle.VehiclePriceAnalysisRequest;
import com.carselling.oldcar.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Service for vehicle price analysis and valuation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CarPriceService {

    private final CarRepository carRepository;

    /**
     * Analyze price for a vehicle based on market data
     */
    @Transactional(readOnly = true)
    public VehiclePriceAnalysisDto analyzePrice(VehiclePriceAnalysisRequest request) {
        log.debug("Analyzing price for {} {}", request.getMake(), request.getModel());

        List<Object[]> stats = carRepository.getPriceStatistics(
                request.getMake(),
                request.getModel(),
                request.getYearFrom(),
                request.getYearTo(),
                request.getMileageFrom(),
                request.getMileageTo());

        if (stats.isEmpty() || stats.get(0) == null || stats.get(0)[0] == null) {
            return VehiclePriceAnalysisDto.builder()
                    .totalListings(0L)
                    .recommendation("INSUFFICIENT_DATA")
                    .build();
        }

        Object[] result = stats.get(0);
        // Handle potential type differences depending on DB (H2 vs Postgres vs MySQL)
        // AVG usually returns Double
        BigDecimal averagePrice;
        if (result[0] instanceof Double) {
            averagePrice = BigDecimal.valueOf((Double) result[0]);
        } else if (result[0] instanceof BigDecimal) {
            averagePrice = (BigDecimal) result[0];
        } else {
            // Fallback
            averagePrice = BigDecimal.ZERO;
        }
        averagePrice = averagePrice.setScale(2, RoundingMode.HALF_UP);

        BigDecimal minPrice = result[1] != null ? (BigDecimal) result[1] : BigDecimal.ZERO;
        BigDecimal maxPrice = result[2] != null ? (BigDecimal) result[2] : BigDecimal.ZERO;
        Long count = (Long) result[3];

        String recommendation = "FAIR_PRICE";
        BigDecimal currentPrice = request.getCurrentPrice();

        if (currentPrice != null) {
            if (currentPrice.compareTo(averagePrice.multiply(BigDecimal.valueOf(1.1))) > 0) {
                recommendation = "HIGH_PRICE";
            } else if (currentPrice.compareTo(averagePrice.multiply(BigDecimal.valueOf(0.9))) < 0) {
                recommendation = "GREAT_PRICE";
            } else {
                recommendation = "FAIR_PRICE";
            }
        }

        return VehiclePriceAnalysisDto.builder()
                .averagePrice(averagePrice)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .priceRange(maxPrice.subtract(minPrice))
                .totalListings(count)
                .recommendation(recommendation)
                .build();
    }
}
