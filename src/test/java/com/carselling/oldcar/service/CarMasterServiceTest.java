package com.carselling.oldcar.service;

import com.carselling.oldcar.repository.CarMasterRepository;
import com.carselling.oldcar.service.car.CarMasterService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CarMasterServiceTest {

    @Mock
    private CarMasterRepository carMasterRepository;

    @InjectMocks
    private CarMasterService carMasterService;

    @Test
    public void testGetModelSuggestions_WithQuery() {
        String make = "Maruti";
        String query = "Sw";
        List<String> expected = Arrays.asList("Swift", "Swift Dzire");

        when(carMasterRepository.findDistinctModelsByMakeAndQuery(make, query))
                .thenReturn(expected);

        List<String> result = carMasterService.getModelSuggestions(make, query);

        assertEquals(expected, result);
    }

    @Test
    public void testGetModelSuggestions_WithoutQuery() {
        String make = "Maruti";
        List<String> expected = Arrays.asList("Alto", "Swift", "WagonR");

        when(carMasterRepository.findDistinctModelsByMake(make))
                .thenReturn(expected);

        List<String> result = carMasterService.getModelSuggestions(make, null);

        assertEquals(expected, result);
    }

    @Test
    public void testGetModelSuggestions_EmptyMake() {
        List<String> result = carMasterService.getModelSuggestions("", "any");
        assertEquals(Collections.emptyList(), result);
    }
}
