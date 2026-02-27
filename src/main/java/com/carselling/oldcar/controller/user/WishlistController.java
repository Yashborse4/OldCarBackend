package com.carselling.oldcar.controller.user;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.service.user.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Controller for user wishlist management
 */
@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Wishlist", description = "User wishlist management APIs")
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping("/{carId}")
    @Operation(summary = "Toggle car in wishlist", description = "Adds or removes a car from the current user's wishlist")
    public ResponseEntity<ApiResponse<Boolean>> toggleWishlist(@PathVariable Long carId) {
        boolean inWishlist = wishlistService.toggleWishlist(carId);
        String message = inWishlist ? "Car added to wishlist" : "Car removed from wishlist";

        return ResponseEntity.ok(ApiResponse.success(message, inWishlist));
    }

    @GetMapping
    @Operation(summary = "Get user wishlist", description = "Returns a paginated list of cars in the current user's wishlist")
    public ResponseEntity<ApiResponse<Page<CarResponse>>> getWishlist(Pageable pageable) {
        Page<CarResponse> wishlist = wishlistService.getWishlist(pageable);

        return ResponseEntity.ok(ApiResponse.success("Wishlist retrieved successfully", wishlist));
    }

    @GetMapping("/{carId}/status")
    @Operation(summary = "Check wishlist status", description = "Checks if a specific car is in the current user's wishlist")
    public ResponseEntity<ApiResponse<Boolean>> checkWishlistStatus(@PathVariable Long carId) {
        boolean inWishlist = wishlistService.isInWishlist(carId);

        return ResponseEntity.ok(ApiResponse.success("Status retrieved", inWishlist));
    }
}
