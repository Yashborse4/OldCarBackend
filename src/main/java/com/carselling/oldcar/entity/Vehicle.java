package com.carselling.oldcar.entity;

import com.carselling.oldcar.model.Car;

/**
 * Vehicle - Type alias for Car entity to maintain naming consistency in ML services
 * This allows ML services to use "Vehicle" naming while working with Car entities
 */
public class Vehicle extends Car {
    
    // Constructors
    public Vehicle() {
        super();
    }
    
    public Vehicle(Car car) {
        super();
        if (car != null) {
            this.setId(car.getId());
            this.setMake(car.getMake());
            this.setModel(car.getModel());
            this.setYear(car.getYear());
            this.setPrice(car.getPrice());
            this.setDescription(car.getDescription());
            this.setImageUrl(car.getImageUrl());
            this.setOwner(car.getOwner());
            this.setIsActive(car.getIsActive());
            this.setIsFeatured(car.getIsFeatured());
            this.setIsSold(car.getIsSold());
            this.setViewCount(car.getViewCount());
            this.setMileage(car.getMileage());
            this.setFuelType(car.getFuelType());
            this.setTransmission(car.getTransmission());
            this.setColor(car.getColor());
            this.setVin(car.getVin());
            this.setNumberOfOwners(car.getNumberOfOwners());
            this.setFeaturedUntil(car.getFeaturedUntil());
            this.setCreatedAt(car.getCreatedAt());
            this.setUpdatedAt(car.getUpdatedAt());
        }
    }
    
    // Additional ML-specific helper methods
    public String getLocation() {
        // For now, return a placeholder or derive from owner information
        return getOwner() != null ? getOwner().getLocation() : "Unknown";
    }
    
    public void setLocation(String location) {
        // This could be stored in a separate field or derived
        // For now, we'll keep it simple
    }
    
    // Override toString for ML logging
    @Override
    public String toString() {
        return String.format("Vehicle[id=%d, make=%s, model=%s, year=%d, price=%s]", 
                getId(), getMake(), getModel(), getYear(), getPrice());
    }
}
