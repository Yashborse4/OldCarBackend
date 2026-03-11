package com.carselling.oldcar.controller.user;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.user.UserPreferenceDto;
import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.UserPreferenceService;
import com.carselling.oldcar.service.analytics.UserPreferenceScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for user onboarding preferences.
 * Handles saving, updating, and fetching vehicle preferences.
 */
@RestController
@RequestMapping("/api/user/preferences")
@RequiredArgsConstructor
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "User Preferences", description = "User onboarding preference endpoints")
public class UserPreferenceController {

        private final UserPreferenceService preferenceService;
        private final UserPreferenceScoreService scoreService;

        /**
         * Save or update user preferences.
         * Called after onboarding completion or when user updates preferences later.
         * POST /api/user/preferences
         */
        @PostMapping
        @PreAuthorize("hasRole('USER') or hasRole('DEALER') or hasRole('ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Save user preferences", description = "Save or update vehicle type, budget, and usage preferences. Upserts if preferences already exist.")
        // TODO(SeniorEng): Logic - Ensure validation logic caps the number of
        // preference models a single user can have to prevent unbounded storage
        // requests.
        public ResponseEntity<ApiResponse<UserPreferenceDto>> savePreferences(
                        @RequestBody UserPreferenceDto request,
                        Authentication authentication) {

                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                log.info("Saving preferences for user: {}", currentUser.getId());

                UserPreferenceDto saved = preferenceService.saveOrUpdatePreferences(currentUser.getId(), request);

                return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(
                                "Preferences saved successfully",
                                "Your vehicle preferences have been saved.",
                                saved));
        }

        /**
         * Get current user's preferences.
         * Called on app start to sync preferences from backend (reinstall / new device
         * scenario).
         * GET /api/user/preferences
         */
        @GetMapping
        @PreAuthorize("hasRole('USER') or hasRole('DEALER') or hasRole('ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Get user preferences", description = "Fetch saved vehicle preferences. Returns null data if no preferences are set.")
        public ResponseEntity<ApiResponse<UserPreferenceDto>> getPreferences(
                        Authentication authentication) {

                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                log.info("Fetching preferences for user: {}", currentUser.getId());

                UserPreferenceDto preferences = preferenceService.getPreferences(currentUser.getId());

                if (preferences == null) {
                        return ResponseEntity.ok(ApiResponse.success(
                                        "No preferences found",
                                        "You haven't set any preferences yet.",
                                        null));
                }

                return ResponseEntity.ok(ApiResponse.success(
                                "Preferences retrieved successfully",
                                "Your saved vehicle preferences.",
                                preferences));
        }

        /**
         * Update existing user preferences.
         * PUT /api/user/preferences
         */
        @PutMapping
        @PreAuthorize("hasRole('USER') or hasRole('DEALER') or hasRole('ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Update user preferences", description = "Update existing vehicle preferences. Same as POST (upsert behavior).")
        public ResponseEntity<ApiResponse<UserPreferenceDto>> updatePreferences(
                        @RequestBody UserPreferenceDto request,
                        Authentication authentication) {

                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                log.info("Updating preferences for user: {}", currentUser.getId());

                UserPreferenceDto updated = preferenceService.saveOrUpdatePreferences(currentUser.getId(), request);

                return ResponseEntity.ok(ApiResponse.success(
                                "Preferences updated successfully",
                                "Your vehicle preferences have been updated.",
                                updated));
        }

        /**
         * Reset Behavioral Preferences.
         * POST /api/user/preferences/reset-behavior
         */
        @PostMapping("/reset-behavior")
        @PreAuthorize("hasRole('USER') or hasRole('DEALER') or hasRole('ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Reset behavioral preferences", description = "Clears all behavior-driven preferences (views, saves, etc) for the current user.")
        public ResponseEntity<ApiResponse<Void>> resetBehavioralPreferences(Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                log.info("Resetting behavioral preferences for user: {}", currentUser.getId());

                scoreService.resetBehavioralPreferences(currentUser.getId());

                return ResponseEntity.ok(ApiResponse.success(
                                "Preferences reset successfully",
                                "Your behavioral preferences have been cleared. Recommendations will now rely on your static profile and trending cars.",
                                null));
        }
}
