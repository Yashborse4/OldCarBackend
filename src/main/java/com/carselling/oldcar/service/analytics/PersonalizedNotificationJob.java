package com.carselling.oldcar.service.analytics;

import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.UserPreferenceScore;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserPreferenceScoreRepository;
import com.carselling.oldcar.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalizedNotificationJob {

    private final CarRepository carRepository;
    private final UserPreferenceScoreRepository preferenceScoreRepository;
    private final NotificationService notificationService;

    // Run Daily at 10 AM
    @Scheduled(cron = "0 0 10 * * ?")
    public void sendProactiveRecommendations() {
        log.info("Starting Proactive Recommendations Job");

        // Find cars created in the last 24 hours
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        List<Car> newCars = carRepository.findByCreatedAtAfter(yesterday);

        if (newCars.isEmpty()) {
            log.info("No new cars found in the last 24 hours. Skipping proactive notifications.");
            return;
        }

        for (Car car : newCars) {
            if (car.getCategory() == null || car.getMake() == null)
                continue;

            // Find users who have strong preferences for this car's category or make
            // We consider a score > 5.0 as a strong signal
            List<UserPreferenceScore> interestedUsersStr = preferenceScoreRepository
                    .findByAttributeTypeAndAttributeValue(UserPreferenceScore.AttributeType.CATEGORY,
                            car.getCategory());

            interestedUsersStr.addAll(preferenceScoreRepository
                    .findByAttributeTypeAndAttributeValue(UserPreferenceScore.AttributeType.MAKE, car.getMake()));

            Set<Long> uniqueUserIdsToNotify = interestedUsersStr.stream()
                    .filter(score -> score.getScore() > 5.0)
                    .map(UserPreferenceScore::getUserId)
                    .collect(Collectors.toSet());

            log.info("Found {} users powerfully interested in {} {}", uniqueUserIdsToNotify.size(), car.getMake(),
                    car.getModel());

            for (Long userId : uniqueUserIdsToNotify) {
                // To avoid spamming, in a real system we'd check when they were last notified
                // and cap at 1 proactive push per week per user, but for MVP we fire directly.
                String title = "New " + car.getCategory() + " Match!";
                String body = "A newly listed " + car.getMake() + " " + car.getModel() + " in " + car.getLocation()
                        + " matches your preferences.";

                // Assuming we have a standard send method
                try {
                    java.util.Map<String, String> data = new java.util.HashMap<>();
                    data.put("type", "RECOMMENDATION");
                    data.put("carId", car.getId().toString());
                    notificationService.sendToUser(userId, title, body, data);
                } catch (Exception e) {
                    log.warn("Could not dispatch notification for user {}: {}", userId, e.getMessage());
                }
            }
        }

        log.info("Finished Proactive Recommendations Job");
    }
}
