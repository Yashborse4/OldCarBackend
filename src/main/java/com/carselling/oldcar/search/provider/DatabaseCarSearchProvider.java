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

    @Override
    public List<String> suggest(String prefix, int limit) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return List.of();
        }
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, limit);
        return carRepository.findDistinctMakeAndModelBySearchTerm(prefix.trim(), pageable);
    }

    @Override
    public com.carselling.oldcar.dto.car.SuggestionResponseDto suggestRich(String prefix, int limit) {
        if (prefix == null || prefix.trim().length() < 2) {
            return com.carselling.oldcar.dto.car.SuggestionResponseDto.builder()
                    .brands(List.of())
                    .models(List.of())
                    .general(List.of())
                    .build();
        }

        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, limit);

        // Find distinct Brands
        List<String> brands = carRepository.findDistinctMakesBySearchTerm(prefix.trim().toLowerCase(), pageable);

        // Find distinct Models (Make + Model)
        List<String> models = carRepository.findDistinctMakeAndModelBySearchTerm(prefix.trim().toLowerCase(), pageable);

        return com.carselling.oldcar.dto.car.SuggestionResponseDto.builder()
                .brands(brands)
                .models(models)
                .general(List.of())
                .build();
    }

    @Override
    public List<String> getTrendingSearchTerms(int limit) {
        // Fallback trending searches when ElasticSearch is not available
        return List.of("Honda City", "SUVs under 10 Lakhs", "Automatic Cars", "Diesel Cars", "Maruti Swift")
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
}
