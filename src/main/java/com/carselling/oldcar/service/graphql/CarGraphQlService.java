package com.carselling.oldcar.service.graphql;

import com.carselling.oldcar.dto.graphql.CarGraphQLDto;
import java.util.List;

public interface CarGraphQlService {
    List<CarGraphQLDto> getCarsByDealer(String dealerId);
}
