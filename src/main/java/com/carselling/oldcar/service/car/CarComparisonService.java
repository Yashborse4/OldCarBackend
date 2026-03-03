package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.car.CarComparisonDto;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarComparisonService {
    
    private final CarRepository carRepository;
    
    public List<CarComparisonDto> compareCars(List<Long> carIds) {
        List<Car> cars = carRepository.findAllById(carIds);
        return cars.stream()
                .map(this::convertToComparisonDto)
                .collect(Collectors.toList());
    }
    
    public List<CarComparisonDto> getSimilarCars(Long carId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));
        
        List<Car> similarCars = carRepository.findSimilarCars(
                car.getMake(), car.getModel(), car.getYear(), carId);
        
        return similarCars.stream()
                .map(this::convertToComparisonDto)
                .collect(Collectors.toList());
    }
    
    private CarComparisonDto convertToComparisonDto(Car car) {
        return CarComparisonDto.builder()
                .id(car.getId())
                .make(car.getMake())
                .model(car.getModel())
                .year(car.getYear())
                .price(car.getPrice())
                .mileage(car.getMileage())
                .fuelType(car.getFuelType())
                .transmission(car.getTransmission())
                .build();
    }
}
