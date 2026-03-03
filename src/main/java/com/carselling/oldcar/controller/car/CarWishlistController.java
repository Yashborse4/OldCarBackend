package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarWishlistDto;
import com.carselling.oldcar.service.car.CarWishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cars/wishlist")
@RequiredArgsConstructor
public class CarWishlistController {
    
    private final CarWishlistService carWishlistService;
    
    @PostMapping("/add/{carId}")
    public ResponseEntity<CarWishlistDto> addToWishlist(@PathVariable Long carId) {
        return ResponseEntity.ok(carWishlistService.addToWishlist(carId));
    }
    
    @DeleteMapping("/remove/{carId}")
    public ResponseEntity<Void> removeFromWishlist(@PathVariable Long carId) {
        carWishlistService.removeFromWishlist(carId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<CarWishlistDto>> getUserWishlist(@PathVariable Long userId) {
        return ResponseEntity.ok(carWishlistService.getUserWishlist(userId));
    }
    
    @GetMapping("/check/{carId}")
    public ResponseEntity<Boolean> isInWishlist(@PathVariable Long carId) {
        return ResponseEntity.ok(carWishlistService.isInWishlist(carId));
    }
}
