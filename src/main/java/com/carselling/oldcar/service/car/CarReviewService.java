package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.car.CarReviewDto;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarReview;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.CarReviewRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarReviewService {
    
    private final CarReviewRepository carReviewRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    
    public CarReviewDto addReview(Long carId, CarReviewDto reviewDto) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));
        
        User currentUser = getCurrentUser();
        
        CarReview review = CarReview.builder()
                .car(car)
                .user(currentUser)
                .rating(reviewDto.getRating())
                .title(reviewDto.getTitle())
                .comment(reviewDto.getComment())
                .pros(reviewDto.getPros())
                .cons(reviewDto.getCons())
                .build();
        
        carReviewRepository.save(review);
        
        return convertToDto(review);
    }
    
    public List<CarReviewDto> getCarReviews(Long carId) {
        List<CarReview> reviews = carReviewRepository.findByCarId(carId);
        return reviews.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public List<CarReviewDto> getUserReviews(Long userId) {
        List<CarReview> reviews = carReviewRepository.findByUserId(userId);
        return reviews.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public CarReviewDto updateReview(Long reviewId, CarReviewDto reviewDto) {
        CarReview review = carReviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));
        
        review.setRating(reviewDto.getRating());
        review.setTitle(reviewDto.getTitle());
        review.setComment(reviewDto.getComment());
        review.setPros(reviewDto.getPros());
        review.setCons(reviewDto.getCons());
        review.setUpdatedAt(LocalDateTime.now());
        
        carReviewRepository.save(review);
        
        return convertToDto(review);
    }
    
    public void deleteReview(Long reviewId) {
        carReviewRepository.deleteById(reviewId);
    }
    
    private CarReviewDto convertToDto(CarReview review) {
        return CarReviewDto.builder()
                .id(review.getId())
                .carId(review.getCar().getId())
                .userId(review.getUser().getId())
                .userName(review.getUser().getUsername())
                .rating(review.getRating())
                .title(review.getTitle())
                .comment(review.getComment())
                .pros(review.getPros())
                .cons(review.getCons())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
    
    private User getCurrentUser() {
        return userRepository.findById(1L).orElseThrow(() -> new RuntimeException("User not found"));
    }
}
