package com.carselling.oldcar.service.user;

import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.mapper.CarMapper;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing user wishlist (favorite cars)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WishlistService {

    private final CarMapper carMapper;
    private final AuthService authService;

    private final AsyncWishlistWorker asyncWishlistWorker;

    /**
     * Toggle a car in the user's wishlist
     */
    public boolean toggleWishlist(Long carId) {
        User userFromAuth = authService.getCurrentUser();
        Long currentUserId = userFromAuth.getId();

        log.info("Wishlist toggle request for carId: {} by userId: {}", carId, currentUserId);

        // Calculate expected state synchronously from in-memory / cache
        boolean currentlyInWishlist = isInWishlist(carId);
        boolean isAdding = !currentlyInWishlist;

        // Dispatch background worker instead of blocking the HTTP thread with a database lock
        asyncWishlistWorker.processWishlistToggle(currentUserId, carId, isAdding);

        return isAdding;
    }

    /**
     * Get the user's wishlist
     */
    @Transactional(readOnly = true)
    public Page<CarResponse> getWishlist(Pageable pageable) {
        User currentUser = authService.getCurrentUser();

        // Convert Set to List and handle pagination manually as it's a small set
        // usually
        // For larger sets, a custom query in CarRepository join on favorites would be
        // better
        List<CarResponse> wishlist = currentUser.getFavoriteCars().stream()
                .map(carMapper::toResponse)
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), wishlist.size());

        if (start > wishlist.size()) {
            return new PageImpl<>(new ArrayList<>(), pageable, wishlist.size());
        }

        return new PageImpl<>(wishlist.subList(start, end), pageable, wishlist.size());
    }

    /**
     * Check if a car is in the wishlist
     */
    @Transactional(readOnly = true)
    public boolean isInWishlist(Long carId) {
        User currentUser = authService.getCurrentUser();
        return currentUser.getFavoriteCars().stream()
                .anyMatch(car -> car.getId().equals(carId));
    }
}
