package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.car.CarAlertDto;
import com.carselling.oldcar.model.CarAlert;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarAlertRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarAlertService {
    
    private final CarAlertRepository carAlertRepository;
    private final UserRepository userRepository;
    
    public CarAlertDto createAlert(CarAlertDto alertDto) {
        User currentUser = getCurrentUser();
        
        CarAlert alert = CarAlert.builder()
                .user(currentUser)
                .make(alertDto.getMake())
                .model(alertDto.getModel())
                .minYear(alertDto.getMinYear())
                .maxYear(alertDto.getMaxYear())
                .minPrice(alertDto.getMinPrice())
                .maxPrice(alertDto.getMaxPrice())
                .isActive(true)
                .build();
        
        carAlertRepository.save(alert);
        
        return convertToDto(alert);
    }
    
    public List<CarAlertDto> getUserAlerts(Long userId) {
        List<CarAlert> alerts = carAlertRepository.findByUserId(userId);
        return alerts.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public CarAlertDto updateAlert(Long alertId, CarAlertDto alertDto) {
        CarAlert alert = carAlertRepository.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        
        alert.setMake(alertDto.getMake());
        alert.setModel(alertDto.getModel());
        alert.setMinYear(alertDto.getMinYear());
        alert.setMaxYear(alertDto.getMaxYear());
        alert.setMinPrice(alertDto.getMinPrice());
        alert.setMaxPrice(alertDto.getMaxPrice());
        
        carAlertRepository.save(alert);
        
        return convertToDto(alert);
    }
    
    public void deleteAlert(Long alertId) {
        carAlertRepository.deleteById(alertId);
    }
    
    private CarAlertDto convertToDto(CarAlert alert) {
        return CarAlertDto.builder()
                .id(alert.getId())
                .make(alert.getMake())
                .model(alert.getModel())
                .minYear(alert.getMinYear())
                .maxYear(alert.getMaxYear())
                .minPrice(alert.getMinPrice())
                .maxPrice(alert.getMaxPrice())
                .isActive(alert.getIsActive())
                .createdDate(alert.getCreatedAt())
                .build();
    }
    
    private User getCurrentUser() {
        return userRepository.findById(1L).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
