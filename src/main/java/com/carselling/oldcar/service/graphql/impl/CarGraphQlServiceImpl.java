package com.carselling.oldcar.service.graphql.impl;

import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.dto.graphql.CarGraphQLDto;
import com.carselling.oldcar.dto.user.UserSummary;
import com.carselling.oldcar.service.car.CarService;
import com.carselling.oldcar.service.graphql.CarGraphQlService;
import com.carselling.oldcar.util.PageableUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarGraphQlServiceImpl implements CarGraphQlService {

    private final CarService carService;

    @Override
    public List<CarGraphQLDto> getCarsByDealer(String dealerId) {
        // Fetch DTOs from service
        Pageable pageable = PageableUtils.createPageable(0, 50);
        return carService.getVehiclesByDealer(dealerId, null, pageable).getContent().stream()
                .map(this::mapResponseToGraphQLDto)
                .toList();
    }

    private CarGraphQLDto mapResponseToGraphQLDto(CarResponse dto) {
        return CarGraphQLDto.builder()
                .id(dto.getId())
                .make(dto.getMake())
                .model(dto.getModel())
                .year(dto.getYear())
                .price(dto.getPrice() != null ? BigDecimal.valueOf(dto.getPrice()) : null)
                .condition(dto.getCondition())
                .description(dto.getDescription()) // Now available
                .imageUrl(dto.getImages() != null && !dto.getImages().isEmpty() ? dto.getImages().get(0) : null)
                .images(dto.getImages())
                .videoUrl(dto.getVideoUrl())
                // .status(dto.getStatus()) // if present
                .mileage(dto.getMileage() != null ? dto.getMileage().intValue() : null)
                .transmission(dto.getSpecifications() != null ? dto.getSpecifications().getTransmission() : null)
                .fuelType(dto.getSpecifications() != null ? dto.getSpecifications().getFuelType() : null)
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                // Optimize: Set empty/id-only owner, let DataFetcher resolve full details if
                // needed
                .owner(UserSummary.builder().id(Long.parseLong(dto.getDealerId())).build())
                .build();
    }
}
