package com.carselling.oldcar.controller.user;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.user.UpdateUserRequest;
import com.carselling.oldcar.dto.user.UserResponse;
import com.carselling.oldcar.service.user.UserService;
import com.carselling.oldcar.service.user.DealerVerificationService;
import com.carselling.oldcar.annotation.RateLimit;
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
@io.swagger.v3.oas.annotations.tags.Tag(name = "User Profile", description = "User profile management endpoints")
public class UserController {

        private final UserService userService;
        private final DealerVerificationService dealerVerificationService;

        /**
         * Get current user's profile
         * GET /api/user/profile
         */
        @GetMapping("/profile")
        @PreAuthorize("hasRole('USER') or hasRole('DEALER') or hasRole('ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Get current profile", description = "Retrieve the profile of the currently logged-in user")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
        })
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Get user profile by ID", description = "Retrieve a specific user profile (User/Dealer/Admin)")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User profile retrieved"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
        })
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Update profile", description = "Update the profile information of the current user")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Profile updated successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
        })
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Request dealer role", description = "Submit a request to become a dealer")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dealer role requested"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User already a dealer")
        })
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Update user profile (Admin)", description = "Admin update of user profile")
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Delete account", description = "Delete current user account")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account deleted"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
        })
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Delete user (Admin)", description = "Admin delete user account")
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Upload profile image", description = "Upload a new profile image")
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Get profile image", description = "Get current user profile image URL")
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Get user profile image", description = "Get specific user profile image URL")
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Delete profile image", description = "Remove current user profile image")
        public ResponseEntity<ApiResponse<Object>> deleteProfileImage() {
                log.info("Deleting profile image for current user");

                userService.deleteProfileImage();

                return ResponseEntity.ok(ApiResponse.success(
                                "Profile image deleted successfully",
                                "Your profile image has been removed from the database."));
        }

        /**
         * Search dealers (for co-listing)
         * GET /api/user/search/dealers
         */
        @GetMapping("/search/dealers")
        @PreAuthorize("hasRole('DEALER') or hasRole('ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Search dealers", description = "Search for dealers by name, username or showroom name")
        public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<UserResponse>>> searchDealers(
                        @RequestParam(required = false) String query,
                        org.springframework.data.domain.Pageable pageable) {
                log.info("Searching dealers with query: {}", query);

                org.springframework.data.domain.Page<UserResponse> dealers = userService.searchDealers(query, pageable);

                return ResponseEntity.ok(ApiResponse.success(
                                "Dealers found",
                                "Found " + dealers.getTotalElements() + " dealers matching your query",
                                dealers));
        }

        // ============ DEALER VERIFICATION ============

        /**
         * Submit dealer verification request
         * POST /api/user/dealer-verification
         */
        @PostMapping("/dealer-verification")
        @PreAuthorize("hasRole('DEALER')")
        @RateLimit(capacity = 3, refill = 1, refillPeriod = 5)
        @io.swagger.v3.oas.annotations.Operation(summary = "Submit dealer verification", description = "Submit verification request (Dealer)")
        public ResponseEntity<ApiResponse<com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto>> submitVerificationRequest(
                        @Valid @RequestBody com.carselling.oldcar.dto.dealer.DealerVerificationRequestDto request,
                        org.springframework.security.core.Authentication authentication) {
                com.carselling.oldcar.model.User dealer = (com.carselling.oldcar.model.User) authentication
                                .getPrincipal();
                log.info("Dealer {} submitting verification request via /api/user/dealer-verification", dealer.getId());
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
        @RateLimit(capacity = 20, refill = 5, refillPeriod = 1)
        @io.swagger.v3.oas.annotations.Operation(summary = "Get verification status", description = "Get current dealer verification status")
        public ResponseEntity<ApiResponse<com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto>> getMyVerificationStatus(
                        org.springframework.security.core.Authentication authentication) {
                com.carselling.oldcar.model.User dealer = (com.carselling.oldcar.model.User) authentication
                                .getPrincipal();
                log.info("Dealer {} getting verification status via /api/user/dealer-verification", dealer.getId());
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
