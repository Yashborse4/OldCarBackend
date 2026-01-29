package com.carselling.oldcar.mapper;

import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.User;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * Mapper for converting Car entity to DTOs.
 */
@Component
public class CarMapper {

    public CarResponse toResponse(Car car) {
        if (car == null) {
            return null;
        }

        User owner = car.getOwner();

        CarResponse.CarSpecifications specs = CarResponse.CarSpecifications.builder()
                .fuelType(car.getFuelType())
                .transmission(car.getTransmission())
                .color(car.getColor())
                // .engine(car.getEngine()) // Assuming inferred or missing
                // .driveType(car.getDriveType())
                .doors(car.getNumberOfOwners()) // Mapping number of owners to something? No.
                // Specs seem to be missing in Car entity basic fields, maybe in CarMaster or
                // just partial
                .build();

        return CarResponse.builder()
                .id(car.getId().toString())
                .make(car.getMake())
                .model(car.getModel())
                .year(car.getYear())
                .price(car.getPrice() != null ? car.getPrice().longValue() : 0L)
                .mileage(car.getMileage() != null ? car.getMileage().longValue() : 0L)
                .location(car.getLocation())
                .condition("Used") // Default or infer from fields
                .images(car.getImages() != null ? car.getImages() : new ArrayList<>())
                .specifications(specs)
                .dealerId(owner != null ? owner.getId().toString() : null)
                .dealerName(owner != null ? owner.getUsername() : null) // or getBusinessName if available
                .isCoListed(false) // Default
                .views(car.getViewCount())
                .inquiries(car.getInquiryCount())
                .shares(car.getShareCount())
                .status(car.getStatus() != null ? car.getStatus().name() : null)
                .mediaStatus(car.getMediaStatus() != null ? car.getMediaStatus().name() : null)
                .isSold(car.getIsSold())
                .isAvailable(car.getIsAvailable())
                .isFeatured(car.getIsFeatured())
                .carMasterId(car.getCarMaster() != null ? car.getCarMaster().getId() : null)
                .createdAt(car.getCreatedAt())
                .updatedAt(car.getUpdatedAt())
                .build();
    }
}
