package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.car.CarTestDriveDto;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarTestDrive;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.CarTestDriveRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarTestDriveService {
    
    private final CarTestDriveRepository carTestDriveRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    
    public CarTestDriveDto scheduleTestDrive(Long carId, CarTestDriveDto testDriveDto) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));
        
        User currentUser = getCurrentUser();
        
        CarTestDrive testDrive = CarTestDrive.builder()
                .car(car)
                .user(currentUser)
                .scheduledDate(testDriveDto.getScheduledDate())
                .status("SCHEDULED")
                .contactNumber(testDriveDto.getContactNumber())
                .message(testDriveDto.getMessage())
                .build();
        
        carTestDriveRepository.save(testDrive);
        
        return convertToDto(testDrive);
    }
    
    public List<CarTestDriveDto> getUserTestDrives(Long userId) {
        List<CarTestDrive> testDrives = carTestDriveRepository.findByUserId(userId);
        return testDrives.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<CarTestDriveDto> getCarTestDrives(Long carId) {
        List<CarTestDrive> testDrives = carTestDriveRepository.findByCarId(carId);
        return testDrives.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public CarTestDriveDto confirmTestDrive(Long testDriveId) {
        CarTestDrive testDrive = carTestDriveRepository.findById(testDriveId)
                .orElseThrow(() -> new RuntimeException("Test drive not found"));
        
        testDrive.setStatus("CONFIRMED");
        testDrive.setConfirmedDate(LocalDateTime.now());
        
        carTestDriveRepository.save(testDrive);
        
        return convertToDto(testDrive);
    }
    
    public CarTestDriveDto cancelTestDrive(Long testDriveId) {
        CarTestDrive testDrive = carTestDriveRepository.findById(testDriveId)
                .orElseThrow(() -> new RuntimeException("Test drive not found"));
        
        testDrive.setStatus("CANCELLED");
        testDrive.setCancelledDate(LocalDateTime.now());
        
        carTestDriveRepository.save(testDrive);
        
        return convertToDto(testDrive);
    }
    
    private CarTestDriveDto convertToDto(CarTestDrive testDrive) {
        return CarTestDriveDto.builder()
                .id(testDrive.getId())
                .carId(testDrive.getCar().getId())
                .carMake(testDrive.getCar().getMake())
                .carModel(testDrive.getCar().getModel())
                .scheduledDate(testDrive.getScheduledDate())
                .confirmedDate(testDrive.getConfirmedDate())
                .cancelledDate(testDrive.getCancelledDate())
                .status(testDrive.getStatus())
                .contactNumber(testDrive.getContactNumber())
                .message(testDrive.getMessage())
                .build();
    }
    
    private User getCurrentUser() {
        return userRepository.findById(1L).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
