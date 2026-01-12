package com.carselling.oldcar.service;

import com.carselling.oldcar.repository.CarMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CarMasterService {

    private final CarMasterRepository carMasterRepository;

    public List<String> getModelSuggestions(String make, String query) {
        if (make == null || make.trim().isEmpty()) {
            return Collections.emptyList();
        }

        if (query != null && !query.trim().isEmpty()) {
            return carMasterRepository.findDistinctModelsByMakeAndQuery(make, query);
        } else {
            return carMasterRepository.findDistinctModelsByMake(make);
        }
    }
}
