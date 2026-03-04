package com.carselling.oldcar.service.analytics;

import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.UserPreferenceScore;
import com.carselling.oldcar.repository.UserPreferenceScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.lang.String;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPreferenceScoreService {

    private final UserPreferenceScoreRepository scoreRepository;

    // Max score to prevent runaway inflation for very active users
    private static final double MAX_SCORE = 100.0;

    /**
     * Updates preference scores based on a user's interaction with a specific car.
     *
     * @param userId The ID of the user
     * @param car    The car they interacted with
     * @param weight The weight of the interaction (e.g., View = 1, Save = 5,
     *               Contact = 10)
     */
    @Async
    @Transactional
    public void updateScoreForCarInteraction(Long userId, Car car, double weight) {
        if (userId == null || car == null || weight <= 0) {
            return;
        }

        log.debug("Updating preference scores for user {} based on car {} with weight {}", userId, car.getId(), weight);

        // Update Make scoring
        if (car.getMake() != null && !car.getMake().isEmpty()) {
            incrementScore(userId, UserPreferenceScore.AttributeType.MAKE, car.getMake().toUpperCase(), weight);
        }

        // Update Category / Body Type scoring
        if (car.getCategory() != null && !car.getCategory().isEmpty()) {
            incrementScore(userId, UserPreferenceScore.AttributeType.CATEGORY, car.getCategory().toUpperCase(), weight);
        }

        // Update Fuel Type scoring
        if (car.getFuelType() != null && !car.getFuelType().isEmpty()) {
            incrementScore(userId, UserPreferenceScore.AttributeType.FUEL_TYPE, car.getFuelType().toUpperCase(),
                    weight);
        }

        // Update Price Bucket scoring
        if (car.getPrice() != null) {
            String priceBucket = determinePriceBucket(car.getPrice());
            incrementScore(userId, UserPreferenceScore.AttributeType.PRICE_BUCKET, priceBucket, weight);
        }
    }

    /**
     * Updates preference scores based on search queries or filters applied.
     * Extracts values from event metadata.
     */
    @Async
    @Transactional
    public void updateScoreFromMetadata(Long userId, Map<String, Object> metadata, double weight) {
        if (userId == null || metadata == null || weight <= 0) {
            return;
        }

        log.debug("Updating preference scores for user {} from metadata with weight {}", userId, weight);

        Object makeObj = metadata.get("make");
        if (makeObj != null && !makeObj.toString().isEmpty()) {
            incrementScore(userId, UserPreferenceScore.AttributeType.MAKE, makeObj.toString().toUpperCase(), weight);
        }

        Object categoryObj = metadata.get("category");
        if (categoryObj != null && !categoryObj.toString().isEmpty()) {
            incrementScore(userId, UserPreferenceScore.AttributeType.CATEGORY, categoryObj.toString().toUpperCase(),
                    weight);
        }

        Object fuelObj = metadata.get("fuelType");
        if (fuelObj != null && !fuelObj.toString().isEmpty()) {
            incrementScore(userId, UserPreferenceScore.AttributeType.FUEL_TYPE, fuelObj.toString().toUpperCase(),
                    weight);
        }

        Object bodyObj = metadata.get("bodyType");
        if (bodyObj != null && !bodyObj.toString().isEmpty()) {
            incrementScore(userId, UserPreferenceScore.AttributeType.BODY_TYPE, bodyObj.toString().toUpperCase(),
                    weight);
        }

        Object queryObj = metadata.get("query");
        if (queryObj != null && !queryObj.toString().isEmpty()) {
            // Very naive way to match queries against makes or categories
            String query = queryObj.toString().toUpperCase();
            // In a real app we'd map this, for now just store it as a generic category if
            // it matches some list
            // Or just skip for MVP
        }
    }

    private void incrementScore(Long userId, UserPreferenceScore.AttributeType type, String value, double weight) {
        Optional<UserPreferenceScore> existingOpt = scoreRepository
                .findByUserIdAndAttributeTypeAndAttributeValue(userId, type, value);

        if (existingOpt.isPresent()) {
            UserPreferenceScore existing = existingOpt.get();
            double newScore = Math.min(existing.getScore() + weight, MAX_SCORE);
            existing.setScore(newScore);
            scoreRepository.save(existing);
        } else {
            UserPreferenceScore newScore = UserPreferenceScore.builder()
                    .userId(userId)
                    .attributeType(type)
                    .attributeValue(value)
                    .score(Math.min(weight, MAX_SCORE))
                    .build();
            scoreRepository.save(newScore);
        }
    }

    private String determinePriceBucket(BigDecimal price) {
        double p = price.doubleValue();
        if (p < 200000)
            return "BELOW_2L";
        if (p < 500000)
            return "2L_5L";
        if (p < 1000000)
            return "5L_10L";
        if (p < 2000000)
            return "10L_20L";
        return "ABOVE_20L";
    }

    /**
     * Resets/Deletes all behavioral scores for a user.
     */
    @Transactional
    public void resetBehavioralPreferences(Long userId) {
        log.info("Resetting behavior scores for user {}", userId);
        scoreRepository.deleteByUserId(userId);
    }

    /**
     * Retrieves the top 5 highest scored preferences for a user
     */
    @Transactional(readOnly = true)
    public java.util.List<UserPreferenceScore> getUserTopPreferences(Long userId) {
        if (userId == null)
            return java.util.Collections.emptyList();

        java.util.List<UserPreferenceScore> allScores = scoreRepository.findByUserIdOrderByScoreDesc(userId);
        return allScores.stream().limit(5).collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public int decayScores(java.time.LocalDateTime cutoff, double decayFactor) {
        return scoreRepository.decayOldScores(cutoff, decayFactor);
    }

    @Transactional
    public int deleteScoresBelowThreshold(double threshold) {
        return scoreRepository.deleteScoresBelowThreshold(threshold);
    }
}
