package com.carselling.oldcar.mapper;

import com.carselling.oldcar.document.VehicleSearchDocument;
import com.carselling.oldcar.model.User;
import org.springframework.stereotype.Component;

@Component
public class VehicleSearchResultMapper {

    public VehicleSearchDocument applyRoleBasedMasking(VehicleSearchDocument document, User currentUser) {
        return document;
    }
}

