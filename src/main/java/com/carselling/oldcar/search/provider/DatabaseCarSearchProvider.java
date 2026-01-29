package com.carselling.oldcar.search.provider;

import com.carselling.oldcar.dto.car.CarSearchCriteria;
import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchHitDto;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.search.CarSearchProvider;
import com.carselling.oldcar.specification.CarSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Database implementation of CarSearchProvider.
 * Used as fallback or primary when Elasticsearch is disabled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class DatabaseCarSearchProvider implements CarSearchProvider {

    private final CarRepository carRepository;

    @Override
    public Page<CarSearchHitDto> search(CarSearchCriteria criteria, Pageable pageable) {
        log.debug("Executing Database search for criteria: {}", criteria);

        // Direct specification usage
        Specification<Car> spec = CarSpecification.getCarsByCriteria(criteria);
        Page<Car> cars = carRepository.findAll(spec, pageable);

        // Convert to DTO
        List<CarSearchHitDto> hits = cars.getContent().stream()
                .map(this::convertCarToHitDto)
                .collect(Collectors.toList());

        return new PageImpl<>(hits, pageable, cars.getTotalElements());
    }

    private CarSearchHitDto convertCarToHitDto(Car car) {
        return CarSearchHitDto.builder()
                .id(car.getId().toString())
                .brand(car.getMake())
                .model(car.getModel())
                .year(car.getYear())
                .price(car.getPrice())
                .mileage(car.getMileage())
                .fuelType(car.getFuelType())
                .transmission(car.getTransmission())
                .city(car.getOwner() != null ? car.getOwner().getLocation() : null)
                .thumbnailUrl(car.getImageUrl())
                // .videoUrl(car.getVideoUrl()) // If DTO has it
                .dealerId(car.getOwner() != null ? car.getOwner().getId().toString() : null)
                .isPromoted(Boolean.TRUE.equals(car.getIsFeatured()))
                .createdAt(car.getCreatedAt())
                .build();
    }
}
