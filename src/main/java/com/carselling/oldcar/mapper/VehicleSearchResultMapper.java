package com.carselling.oldcar.mapper;

import com.carselling.oldcar.document.VehicleSearchDocument;
import com.carselling.oldcar.dto.car.CarSearchDtos.CarSearchHitDto;
import org.springframework.stereotype.Component;

@Component
public class VehicleSearchResultMapper {

    public CarSearchHitDto toDto(VehicleSearchDocument document) {
        if (document == null)
            return null;
        return CarSearchHitDto.fromDocument(document);
    }
}
