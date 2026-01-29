package com.carselling.oldcar.service.analytics;

import com.carselling.oldcar.repository.CarRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

/**
 * Service to register custom business metrics with Micrometer
 */
@Service
public class MetricsService {

    public MetricsService(MeterRegistry registry, CarRepository carRepository) {
        // Business Metric: Total Active Cars
        Gauge.builder("business.cars.active", carRepository,
                repo -> repo.findAllActiveCars().size()) // Use count query if available for performance
                .description("Number of active cars listed on the platform")
                .register(registry);

        // We could optimize this to use a count query instead of fetching all list
        // but for now this demonstrates the capability.
    }
}
