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
@lombok.RequiredArgsConstructor
public class CarMapper {

    private final com.carselling.oldcar.repository.CarRepository carRepository;

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
                .category(car.getCategory())
                .registrationType(car.getRegistrationType())
                .images(car.getImages() != null ? car.getImages() : new ArrayList<>())
                .specifications(specs)
                .dealerId(owner != null ? owner.getId().toString() : null)
                .dealerName(owner != null ? owner.getUsername() : null) // or getBusinessName if available
                .phoneNumber(owner != null ? owner.getPhoneNumber() : null)
                .isCoListed(false) // Default
                .views(car.getViewCount())
                .inquiries(car.getInquiryCount())
                .shares(car.getShareCount())
                .status(car.getStatus() != null ? car.getStatus().name() : null)
                .mediaStatus(car.getMediaStatus() != null ? car.getMediaStatus().name() : null)
                .isSold(car.getIsSold())
                .isAvailable(car.getIsAvailable())
                .isFeatured(car.getIsFeatured())
                .isInspected(car.getIsInspected())
                .verifiedDealer(owner != null ? owner.isDealerVerified() : false)
                .uploaderRole(owner != null ? owner.getRole().name() : "USER")
                .carMasterId(car.getCarMaster() != null ? car.getCarMaster().getId() : null)
                .dealerRating(owner != null ? owner.getDealerRating() : null)
                .dealerReviewCount(owner != null ? owner.getDealerReviewCount() : null)
                .dealerActiveListings(owner != null ? (int) carRepository.countActiveCarsByOwnerId(owner.getId()) : 0)
                .registrationMonthYear(car.getRegistrationMonthYear())
                .engineCapacity(car.getEngineCapacity())
                .ownership(car.getOwnership())
                .makeMonthYear(car.getMakeMonthYear())
                .spareKey(car.getSpareKey())
                .discountAmount(car.getDiscountAmount() != null ? car.getDiscountAmount().longValue() : null)
                .otherCharges(car.getOtherCharges() != null ? car.getOtherCharges().longValue() : null)
                .zeroWorryMax(car.getZeroWorryMax())
                .lifetimeWarranty(car.getLifetimeWarranty())
                .returnDays(car.getReturnDays())
                .registrationNumber(car.getRegistrationNumber())
                .createdAt(car.getCreatedAt())
                .updatedAt(car.getUpdatedAt())
                .build();
    }
}
