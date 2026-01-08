package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.user.UpdateUserRequest;
import com.carselling.oldcar.dto.user.UserResponse;
import com.carselling.oldcar.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * User Controller for user profile management
 * Handles user profile operations and user searches
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {

        private final UserService userService;
        private final com.carselling.oldcar.service.DealerVerificationService dealerVerificationService;

        /**
         * Get current user's profile
         * GET /api/user/profile
         */
        @GetMapping("/profile")
        @PreAuthorize("hasRole('USER') or hasRole('DEALER') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<UserResponse>> getCurrentUserProfile() {
                log.info("Getting current user profile");

                UserResponse userResponse = userService.getCurrentUserProfile();

                return ResponseEntity.ok(ApiResponse.success(
                                "Profile retrieved successfully",
                                "Your profile information has been retrieved.",
                                userResponse));
        }

        /**
         * Get user profile by ID
         * GET /api/user/{id}
         */
        @GetMapping("/{id}")
        @PreAuthorize("hasRole('USER') or hasRole('DEALER') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<UserResponse>> getUserProfile(@PathVariable Long id) {
                log.info("Getting user profile for ID: {}", id);

                UserResponse userResponse = userService.getUserProfile(id);

                return ResponseEntity.ok(ApiResponse.success(
                                "User profile retrieved successfully",
                                "The requested user profile has been retrieved.",
                                userResponse));
        }

        /**
         * Update current user's profile
         * PUT /api/user/profile
         */
        @PutMapping("/profile")
        @PreAuthorize("hasRole('USER') or hasRole('DEALER') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUserProfile(
                        @Valid @RequestBody UpdateUserRequest request) {
                log.info("Updating current user profile");

                UserResponse currentUser = userService.getCurrentUserProfile();
                UserResponse updatedUser = userService.updateUserProfile(currentUser.getId(), request);

                return ResponseEntity.ok(ApiResponse.success(
                                "Profile updated successfully",
                                "Your profile has been updated with the provided information.",
                                updatedUser));
        }

        /**
         * Request to become a Dealer
         * POST /api/user/request-dealer
         */
        @PostMapping("/request-dealer")
        @PreAuthorize("hasRole('USER')")
        public ResponseEntity<ApiResponse<UserResponse>> requestDealerRole() {
                log.info("User requesting dealer role");

                UserResponse updatedUser = userService.requestDealerRole();

                return ResponseEntity.ok(ApiResponse.success(
                                "Dealer role requested",
                                "Your account is now pending dealer verification. You can list cars, but they will not be public until verified.",
                                updatedUser));
        }

        /**
         * Update user profile by ID (admin or owner only)
         * PUT /api/user/{id}
         */
        @PutMapping("/{id}")
        @PreAuthorize("hasRole('ADMIN') or @authService.isOwner(#id)")
        public ResponseEntity<ApiResponse<UserResponse>> updateUserProfile(
                        @PathVariable Long id,
                        @Valid @RequestBody UpdateUserRequest request) {
                log.info("Updating user profile for ID: {}", id);

                UserResponse updatedUser = userService.updateUserProfile(id, request);

                return ResponseEntity.ok(ApiResponse.success(
                                "Profile updated successfully",
                                "The user profile has been updated with the provided information.",
                                updatedUser));
        }

        /**
         * Delete current user's account
         * DELETE /api/user/profile
         */
        @DeleteMapping("/profile")
        @PreAuthorize("hasRole('USER') or hasRole('DEALER') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<Object>> deleteCurrentUserAccount() {
                log.info("Deleting current user account");

                UserResponse currentUser = userService.getCurrentUserProfile();
                userService.deleteUserAccount(currentUser.getId());

                return ResponseEntity.ok(ApiResponse.success(
                                "Account deleted successfully",
                                "Your account has been deactivated. You can contact support to reactivate it."));
        }

        /**
         * Delete user account by ID (admin or owner only)
         * DELETE /api/user/{id}
         */
        @DeleteMapping("/{id}")
        @PreAuthorize("hasRole('ADMIN') or @authService.isOwner(#id)")
        public ResponseEntity<ApiResponse<Object>> deleteUserAccount(@PathVariable Long id) {
                log.info("Deleting user account for ID: {}", id);

                userService.deleteUserAccount(id);

                return ResponseEntity.ok(ApiResponse.success(
                                "Account deleted successfully",
                                "The user account has been deactivated."));
        }

        /**
         * Upload profile image for current user
         * POST /api/user/profile/image
         */
        @PostMapping("/profile/image")
        @PreAuthorize("hasRole('USER') or hasRole('BUYER') or hasRole('DEALER') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<java.util.Map<String, String>>> uploadProfileImage(
                        @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
                log.info("Uploading profile image for current user");

                String imageUrl = userService.uploadProfileImage(file);

                return ResponseEntity.ok(ApiResponse.success(
                                "Profile image uploaded successfully",
                                "Your profile image has been uploaded.",
                                java.util.Map.of("imageUrl", imageUrl)));
        }

        /**
         * Get current user's profile image URL
         * GET /api/user/profile/image
         */
        @GetMapping("/profile/image")
        @PreAuthorize("hasRole('USER') or hasRole('BUYER') or hasRole('DEALER') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<java.util.Map<String, String>>> getProfileImage() {
                log.info("Getting profile image for current user");

                UserResponse user = userService.getCurrentUserProfile();

                if (user.getProfileImageUrl() == null) {
                        return ResponseEntity.notFound().build();
                }

                return ResponseEntity.ok(ApiResponse.success(
                                "Profile image retrieved",
                                "Profile image URL retrieved successfully",
                                java.util.Map.of("imageUrl", user.getProfileImageUrl())));
        }

        /**
         * Get user's profile image URL by ID
         * GET /api/user/{id}/image
         */
        @GetMapping("/{id}/image")
        @PreAuthorize("hasRole('USER') or hasRole('DEALER') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<java.util.Map<String, String>>> getUserProfileImage(@PathVariable Long id) {
                log.info("Getting profile image for user ID: {}", id);

                UserResponse user = userService.getUserProfile(id);

                if (user.getProfileImageUrl() == null) {
                        return ResponseEntity.notFound().build();
                }

                return ResponseEntity.ok(ApiResponse.success(
                                "Profile image retrieved",
                                "Profile image URL retrieved successfully",
                                java.util.Map.of("imageUrl", user.getProfileImageUrl())));
        }

        /**
         * Delete current user's profile image
         * DELETE /api/user/profile/image
         */
        @DeleteMapping("/profile/image")
        @PreAuthorize("hasRole('USER') or hasRole('BUYER') or hasRole('DEALER') or hasRole('ADMIN')")
        public ResponseEntity<ApiResponse<Object>> deleteProfileImage() {
                log.info("Deleting profile image for current user");

                userService.deleteProfileImage();

                return ResponseEntity.ok(ApiResponse.success(
                                "Profile image deleted successfully",
                                "Your profile image has been removed from the database."));
        }

        // ============ DEALER VERIFICATION ============

        /**
         * Submit dealer verification request
         * POST /api/user/dealer-verification
         */
        @PostMapping("/dealer-verification")
        @PreAuthorize("hasRole('DEALER')")
        public ResponseEntity<ApiResponse<com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto>> submitVerificationRequest(
                        @Valid @RequestBody com.carselling.oldcar.dto.dealer.DealerVerificationRequestDto request,
                        org.springframework.security.core.Authentication authentication) {
                log.info("Dealer submitting verification request");

                com.carselling.oldcar.model.User dealer = (com.carselling.oldcar.model.User) authentication
                                .getPrincipal();
                com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto response = dealerVerificationService
                                .submitVerificationRequest(dealer.getId(), request);

                return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(ApiResponse.success(
                                "Verification request submitted",
                                "Your verification request has been submitted and is pending admin review.",
                                response));
        }

        /**
         * Get current dealer's verification status
         * GET /api/user/dealer-verification
         */
        @GetMapping("/dealer-verification")
        @PreAuthorize("hasRole('DEALER')")
        public ResponseEntity<ApiResponse<com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto>> getMyVerificationStatus(
                        org.springframework.security.core.Authentication authentication) {
                log.info("Dealer getting verification status");

                com.carselling.oldcar.model.User dealer = (com.carselling.oldcar.model.User) authentication
                                .getPrincipal();
                com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto response = dealerVerificationService
                                .getMyVerificationRequest(dealer.getId());

                if (response == null) {
                        return ResponseEntity.ok(ApiResponse.success(
                                        "No verification request found",
                                        "You have not submitted a verification request yet.",
                                        null));
                }

                return ResponseEntity.ok(ApiResponse.success(
                                "Verification status retrieved",
                                "Your verification request status: " + response.getStatusDisplayName(),
                                response));
        }
}
