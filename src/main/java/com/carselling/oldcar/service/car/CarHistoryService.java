package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.car.CarHistoryDto;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarHistory;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarHistoryRepository;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarHistoryService {
    
    private final CarHistoryRepository carHistoryRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    
    public List<CarHistoryDto> getCarHistory(Long carId) {
        List<CarHistory> history = carHistoryRepository.findByCarIdOrderByEventDateDesc(carId);
        return history.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public CarHistoryDto addHistoryRecord(Long carId, CarHistoryDto historyDto) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));
        
        User currentUser = getCurrentUser();
        
        CarHistory carHistory = CarHistory.builder()
                .car(car)
                .user(currentUser)
                .eventType(historyDto.getEventType())
                .eventDate(historyDto.getEventDate())
                .description(historyDto.getDescription())
                .mileage(historyDto.getMileage())
                .cost(historyDto.getCost())
                .location(historyDto.getLocation())
                .build();
        
        carHistoryRepository.save(carHistory);
        
        return convertToDto(carHistory);
    }
    
    public List<CarHistoryDto> getOwnershipHistory(Long carId) {
        List<CarHistory> history = carHistoryRepository.findByCarIdAndEventTypeOrderByEventDateDesc(carId, "OWNERSHIP_CHANGE");
        return history.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<CarHistoryDto> getMaintenanceHistory(Long carId) {
        List<CarHistory> history = carHistoryRepository.findByCarIdAndEventTypeOrderByEventDateDesc(carId, "MAINTENANCE");
        return history.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<CarHistoryDto> getAccidentHistory(Long carId) {
        List<CarHistory> history = carHistoryRepository.findByCarIdAndEventTypeOrderByEventDateDesc(carId, "ACCIDENT");
        return history.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    private CarHistoryDto convertToDto(CarHistory history) {
        return CarHistoryDto.builder()
                .id(history.getId())
                .carId(history.getCar().getId())
                .userId(history.getUser().getId())
                .eventType(history.getEventType())
                .eventDate(history.getEventDate())
                .description(history.getDescription())
                .mileage(history.getMileage())
                .cost(history.getCost())
                .location(history.getLocation())
                .createdAt(history.getCreatedAt())
                .build();
    }
    
    private User getCurrentUser() {
        return userRepository.findById(1L).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
