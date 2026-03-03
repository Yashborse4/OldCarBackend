package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.car.CarNegotiationDto;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarNegotiation;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarNegotiationRepository;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarNegotiationService {
    
    private final CarNegotiationRepository carNegotiationRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    
    public CarNegotiationDto startNegotiation(Long carId, CarNegotiationDto negotiationDto) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));
        
        User currentUser = getCurrentUser();
        
        CarNegotiation negotiation = CarNegotiation.builder()
                .car(car)
                .buyer(currentUser)
                .seller(car.getUser())
                .initialPrice(car.getPrice())
                .offeredPrice(negotiationDto.getOfferedPrice())
                .status("PENDING")
                .message(negotiationDto.getMessage())
                .build();
        
        carNegotiationRepository.save(negotiation);
        
        return convertToDto(negotiation);
    }
    
    public CarNegotiationDto respondToNegotiation(Long negotiationId, CarNegotiationDto negotiationDto) {
        CarNegotiation negotiation = carNegotiationRepository.findById(negotiationId)
                .orElseThrow(() -> new RuntimeException("Negotiation not found"));
        
        negotiation.setCounterOffer(negotiationDto.getCounterOffer());
        negotiation.setResponseMessage(negotiationDto.getResponseMessage());
        negotiation.setStatus("COUNTERED");
        negotiation.setRespondedAt(LocalDateTime.now());
        
        carNegotiationRepository.save(negotiation);
        
        return convertToDto(negotiation);
    }
    
    public List<CarNegotiationDto> getUserNegotiations(Long userId) {
        List<CarNegotiation> negotiations = carNegotiationRepository.findByBuyerIdOrSellerId(userId, userId);
        return negotiations.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<CarNegotiationDto> getCarNegotiations(Long carId) {
        List<CarNegotiation> negotiations = carNegotiationRepository.findByCarId(carId);
        return negotiations.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public CarNegotiationDto acceptNegotiation(Long negotiationId) {
        CarNegotiation negotiation = carNegotiationRepository.findById(negotiationId)
                .orElseThrow(() -> new RuntimeException("Negotiation not found"));
        
        negotiation.setStatus("ACCEPTED");
        negotiation.setFinalPrice(negotiation.getCounterOffer() != null ? negotiation.getCounterOffer() : negotiation.getOfferedPrice());
        negotiation.setCompletedAt(LocalDateTime.now());
        
        carNegotiationRepository.save(negotiation);
        
        return convertToDto(negotiation);
    }
    
    public CarNegotiationDto rejectNegotiation(Long negotiationId) {
        CarNegotiation negotiation = carNegotiationRepository.findById(negotiationId)
                .orElseThrow(() -> new RuntimeException("Negotiation not found"));
        
        negotiation.setStatus("REJECTED");
        negotiation.setCompletedAt(LocalDateTime.now());
        
        carNegotiationRepository.save(negotiation);
        
        return convertToDto(negotiation);
    }
    
    private CarNegotiationDto convertToDto(CarNegotiation negotiation) {
        return CarNegotiationDto.builder()
                .id(negotiation.getId())
                .carId(negotiation.getCar().getId())
                .carMake(negotiation.getCar().getMake())
                .carModel(negotiation.getCar().getModel())
                .buyerId(negotiation.getBuyer().getId())
                .sellerId(negotiation.getSeller().getId())
                .initialPrice(negotiation.getInitialPrice())
                .offeredPrice(negotiation.getOfferedPrice())
                .counterOffer(negotiation.getCounterOffer())
                .finalPrice(negotiation.getFinalPrice())
                .status(negotiation.getStatus())
                .message(negotiation.getMessage())
                .responseMessage(negotiation.getResponseMessage())
                .createdAt(negotiation.getCreatedAt())
                .respondedAt(negotiation.getRespondedAt())
                .completedAt(negotiation.getCompletedAt())
                .build();
    }
    
    private User getCurrentUser() {
        return userRepository.findById(1L).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
