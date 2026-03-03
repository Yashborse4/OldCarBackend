package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarReviewDto;
import com.carselling.oldcar.service.car.CarReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cars/reviews")
@RequiredArgsConstructor
public class CarReviewController {
    
    private final CarReviewService carReviewService;
    
    @PostMapping("/add/{carId}")
    public ResponseEntity<CarReviewDto> addReview(@PathVariable Long carId, @RequestBody CarReviewDto reviewDto) {
        return ResponseEntity.ok(carReviewService.addReview(carId, reviewDto));
    }
    
    @GetMapping("/car/{carId}")
    public ResponseEntity<List<CarReviewDto>> getCarReviews(@PathVariable Long carId) {
        return ResponseEntity.ok(carReviewService.getCarReviews(carId));
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CarReviewDto>> getUserReviews(@PathVariable Long userId) {
        return ResponseEntity.ok(carReviewService.getUserReviews(userId));
    }
    
    @PutMapping("/update/{reviewId}")
    public ResponseEntity<CarReviewDto> updateReview(@PathVariable Long reviewId, @RequestBody CarReviewDto reviewDto) {
        return ResponseEntity.ok(carReviewService.updateReview(reviewId, reviewDto));
    }
    
    @DeleteMapping("/delete/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long reviewId) {
        carReviewService.deleteReview(reviewId);
        return ResponseEntity.ok().build();
    }
}
