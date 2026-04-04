package com.carselling.oldcar.service.user;

import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.service.analytics.UserAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncWishlistWorker {

    private final UserRepository userRepository;
    private final CarRepository carRepository;
    private final UserAnalyticsService analyticsService;

    @Async
    @Transactional
    public void processWishlistToggle(Long userId, Long carId, boolean isAdding) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            Car car = carRepository.findById(carId).orElse(null);

            if (user == null || car == null) {
                log.warn("Async wishlist toggled failed: User {} or Car {} not found", userId, carId);
                return;
            }

            Set<Car> favoriteCars = user.getFavoriteCars();
            if (favoriteCars == null) {
                favoriteCars = new HashSet<>();
                user.setFavoriteCars(favoriteCars);
            }

            if (isAdding) {
                if (favoriteCars.add(car)) {
                    userRepository.save(user);
                    analyticsService.trackCarInteraction(
                            userId,
                            carId,
                            com.carselling.oldcar.model.CarInteractionEvent.EventType.SAVE,
                            null
                    );
                    log.debug("Async added car {} to user {} wishlist", carId, userId);
                }
            } else {
                if (favoriteCars.remove(car)) {
                    userRepository.save(user);
                    analyticsService.trackCarInteraction(
                            userId,
                            carId,
                            com.carselling.oldcar.model.CarInteractionEvent.EventType.UNSAVE,
                            null
                    );
                    log.debug("Async removed car {} from user {} wishlist", carId, userId);
                }
            }
        } catch (Exception e) {
            log.error("Failed async wishlist processing for User {} and Car {}", userId, carId, e);
        }
    }
}
