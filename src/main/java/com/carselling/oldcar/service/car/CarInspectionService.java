package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.car.CarInspectionDto;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarInspection;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarInspectionRepository;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarInspectionService {
    
    private final CarInspectionRepository carInspectionRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    
    public CarInspectionDto scheduleInspection(Long carId, CarInspectionDto inspectionDto) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));
        
        User currentUser = getCurrentUser();
        
        CarInspection inspection = CarInspection.builder()
                .car(car)
                .user(currentUser)
                .scheduledDate(inspectionDto.getScheduledDate())
                .status("SCHEDULED")
                .inspectorName(inspectionDto.getInspectorName())
                .inspectionType(inspectionDto.getInspectionType())
                .build();
        
        carInspectionRepository.save(inspection);
        
        return convertToDto(inspection);
    }
    
    public List<CarInspectionDto> getUserInspections(Long userId) {
        List<CarInspection> inspections = carInspectionRepository.findByUserId(userId);
        return inspections.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<CarInspectionDto> getCarInspections(Long carId) {
        List<CarInspection> inspections = carInspectionRepository.findByCarId(carId);
        return inspections.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public CarInspectionDto completeInspection(Long inspectionId, CarInspectionDto inspectionDto) {
        CarInspection inspection = carInspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new RuntimeException("Inspection not found"));
        
        inspection.setStatus("COMPLETED");
        inspection.setCompletedDate(LocalDateTime.now());
        inspection.setReport(inspectionDto.getReport());
        inspection.setScore(inspectionDto.getScore());
        
        carInspectionRepository.save(inspection);
        
        return convertToDto(inspection);
    }
    
    private CarInspectionDto convertToDto(CarInspection inspection) {
        return CarInspectionDto.builder()
                .id(inspection.getId())
                .carId(inspection.getCar().getId())
                .carMake(inspection.getCar().getMake())
                .carModel(inspection.getCar().getModel())
                .scheduledDate(inspection.getScheduledDate())
                .completedDate(inspection.getCompletedDate())
                .status(inspection.getStatus())
                .inspectorName(inspection.getInspectorName())
                .inspectionType(inspection.getInspectionType())
                .report(inspection.getReport())
                .score(inspection.getScore())
                .build();
    }
    
    private User getCurrentUser() {
        return userRepository.findById(1L).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
