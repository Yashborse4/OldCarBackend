package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.user.UserPreferenceDto;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.UserPreference;
import com.carselling.oldcar.repository.UserPreferenceRepository;
import com.carselling.oldcar.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing user onboarding preferences.
 * Handles save/update/fetch of vehicle type, budget, and usage preferences.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserPreferenceService {

    private final UserPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * Save or update user preferences (upsert).
     * If preferences already exist for the user, they are updated.
     * Also marks onboarding as completed on the User entity.
     *
     * @param userId the user's ID
     * @param dto    the preference data
     * @return the saved/updated preference DTO
     */
    @Transactional
    public UserPreferenceDto saveOrUpdatePreferences(Long userId, UserPreferenceDto dto) {
        log.info("Saving preferences for user ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Optional<UserPreference> existingOpt = preferenceRepository.findByUserId(userId);

        UserPreference preference;
        if (existingOpt.isPresent()) {
            // Update existing preferences
            preference = existingOpt.get();
            log.info("Updating existing preferences for user ID: {}", userId);
        } else {
            // Create new preferences
            preference = UserPreference.builder()
                    .userId(userId)
                    .createdAt(LocalDateTime.now())
                    .build();
            log.info("Creating new preferences for user ID: {}", userId);
        }

        preference.setVehicleTypes(toJson(dto.getVehicleTypes()));
        preference.setBudgetRanges(toJson(dto.getBudgetRanges()));
        preference.setUsagePurposes(toJson(dto.getUsagePurposes()));
        preference.setUpdatedAt(LocalDateTime.now());

        preferenceRepository.save(preference);

        // Mark onboarding as completed
        if (!Boolean.TRUE.equals(user.getOnboardingCompleted())) {
            user.setOnboardingCompleted(true);
            userRepository.save(user);
            log.info("Marked onboarding as completed for user ID: {}", userId);
        }

        return toDto(preference);
    }

    /**
     * Fetch preferences for a user.
     * Called when user reinstalls app or returns after a long time to sync
     * preferences.
     *
     * @param userId the user's ID
     * @return the preference DTO, or null if no preferences exist
     */
    @Transactional(readOnly = true)
    public UserPreferenceDto getPreferences(Long userId) {
        log.info("Fetching preferences for user ID: {}", userId);

        return preferenceRepository.findByUserId(userId)
                .map(this::toDto)
                .orElse(null);
    }

    /**
     * Check if a user has completed onboarding preferences.
     *
     * @param userId the user's ID
     * @return true if preferences exist
     */
    @Transactional(readOnly = true)
    public boolean hasCompletedOnboarding(Long userId) {
        return preferenceRepository.existsByUserId(userId);
    }

    /**
     * Convert preference entity to DTO.
     */
    private UserPreferenceDto toDto(UserPreference preference) {
        return UserPreferenceDto.builder()
                .vehicleTypes(fromJson(preference.getVehicleTypes()))
                .budgetRanges(fromJson(preference.getBudgetRanges()))
                .usagePurposes(fromJson(preference.getUsagePurposes()))
                .build();
    }

    /**
     * Serialize a list of strings to JSON.
     */
    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize list to JSON", e);
            return "[]";
        }
    }

    /**
     * Deserialize a JSON string to a list of strings.
     */
    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON to list", e);
            return Collections.emptyList();
        }
    }
}
