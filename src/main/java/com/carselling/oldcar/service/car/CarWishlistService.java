package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.car.CarWishlistDto;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.WishlistItem;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarWishlistService {
    
    private final WishlistRepository wishlistRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    
    public CarWishlistDto addToWishlist(Long carId) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));
        
        User currentUser = getCurrentUser();
        
        WishlistItem wishlistItem = WishlistItem.builder()
                .user(currentUser)
                .car(car)
                .build();
        
        wishlistRepository.save(wishlistItem);
        
        return convertToDto(wishlistItem);
    }
    
    public void removeFromWishlist(Long carId) {
        User currentUser = getCurrentUser();
        wishlistRepository.deleteByUserIdAndCarId(currentUser.getId(), carId);
    }
    
    public List<CarWishlistDto> getUserWishlist(Long userId) {
        List<WishlistItem> wishlistItems = wishlistRepository.findByUserId(userId);
        return wishlistItems.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public boolean isInWishlist(Long carId) {
        User currentUser = getCurrentUser();
        return wishlistRepository.existsByUserIdAndCarId(currentUser.getId(), carId);
    }
    
    private CarWishlistDto convertToDto(WishlistItem item) {
        return CarWishlistDto.builder()
                .id(item.getId())
                .carId(item.getCar().getId())
                .carMake(item.getCar().getMake())
                .carModel(item.getCar().getModel())
                .carYear(item.getCar().getYear())
                .carPrice(item.getCar().getPrice())
                .addedDate(item.getCreatedAt())
                .build();
    }
    
    private User getCurrentUser() {
        return userRepository.findById(1L).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
