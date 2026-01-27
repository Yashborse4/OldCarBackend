package com.carselling.oldcar.exception;

public class CarNotFoundException extends ResourceNotFoundException {
    public CarNotFoundException(String id) {
        super("Car", "id", id);
    }
}
