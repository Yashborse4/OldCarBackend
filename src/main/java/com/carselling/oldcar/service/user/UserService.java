package com.carselling.oldcar.service.user;

import com.carselling.oldcar.dto.UserStatistics;
import com.carselling.oldcar.dto.user.UpdateUserRequest;
import com.carselling.oldcar.dto.user.UserResponse;
import com.carselling.oldcar.exception.InsufficientPermissionException;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.service.auth.AuthService;
import com.carselling.oldcar.b2.B2FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * User Service for user management operations
 * Handles user profile operations, user searches, and user management
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final B2FileService b2FileService;

    /**
     * Get user profile by ID
     */
    @Transactional(readOnly = true)
    public UserResponse getUserProfile(Long userId) {
        log.info("Retrieving user profile for ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Check if current user can view this profile
        User currentUser = authService.getCurrentUser();
        if (!canViewUserProfile(currentUser, user)) {
            throw new InsufficientPermissionException("You don't have permission to view this user profile");
        }

        return convertToUserResponse(user);
    }

    /**
     * Get current user's profile
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUserProfile() {
        log.info("Retrieving current user profile");

        User currentUser = authService.getCurrentUser();
        return convertToUserResponse(currentUser);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Update user profile
     * Note: Email and phone number are NOT editable for security reasons
     */
    public UserResponse updateUserProfile(Long userId, UpdateUserRequest request) {
        log.info("Updating user profile for ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Check if current user can update this profile
        User currentUser = authService.getCurrentUser();
        if (!canUpdateUserProfile(currentUser, user)) {
            throw new InsufficientPermissionException("You don't have permission to update this user profile");
        }

        // Update user fields
        boolean updated = false;

        if (StringUtils.hasText(request.getUsername()) && !request.getUsername().equals(user.getUsername())) {
            // Check if username is already in use
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new IllegalArgumentException("Username is already in use by another user");
            }
            user.setUsername(request.getUsername());
            updated = true;
        }

        if (StringUtils.hasText(request.getFirstName()) && !request.getFirstName().equals(user.getFirstName())) {
            user.setFirstName(request.getFirstName());
            updated = true;
        }

        if (StringUtils.hasText(request.getLastName()) && !request.getLastName().equals(user.getLastName())) {
            user.setLastName(request.getLastName());
            updated = true;
        }

        if (StringUtils.hasText(request.getLocation()) && !request.getLocation().equals(user.getLocation())) {
            user.setLocation(request.getLocation());
            updated = true;
        }

        // Handle dealer-specific fields
        if (StringUtils.hasText(request.getDealerName())) {
            user.setDealerName(request.getDealerName());
            updated = true;
        }

        if (StringUtils.hasText(request.getShowroomName())) {
            user.setShowroomName(request.getShowroomName());
            updated = true;
        }

        if (StringUtils.hasText(request.getAddress())) {
            user.setAddress(request.getAddress());
            updated = true;
        }

        if (StringUtils.hasText(request.getCity())) {
            user.setCity(request.getCity());
            updated = true;
        }

        if (request.getLatitude() != null) {
            user.setLatitude(request.getLatitude());
            updated = true;
        }

        if (request.getLongitude() != null) {
            user.setLongitude(request.getLongitude());
            updated = true;
        }

        if (updated) {
            user = userRepository.save(user);
            log.info("User profile updated successfully for user: {}", user.getUsername());
        } else {
            log.info("No changes detected for user profile: {}", user.getUsername());
        }

        return convertToUserResponse(user);
    }

    /**
     * Delete user account
     */
    public void deleteUserAccount(Long userId) {
        log.info("Deleting user account for ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Check if current user can delete this account
        User currentUser = authService.getCurrentUser();
        if (!canDeleteUserAccount(currentUser, user)) {
            throw new InsufficientPermissionException("You don't have permission to delete this user account");
        }

        // Soft delete - deactivate the account instead of hard delete
        user.setIsActive(false);
        userRepository.save(user);

        log.info("User account deactivated successfully for user: {}", user.getUsername());
    }

    /**
     * Request to become a Dealer (User self-service)
     */
    public UserResponse requestDealerRole() {
        User currentUser = authService.getCurrentUser();
        log.info("User requesting dealer role: {}", currentUser.getUsername());

        if (currentUser.getRole() == Role.DEALER || currentUser.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("User is already a dealer or admin");
        }

        currentUser.setRole(Role.DEALER);
        currentUser.updateDealerStatus(
                com.carselling.oldcar.model.DealerStatus.UNVERIFIED,
                "Self-service dealer role request");

        currentUser = userRepository.save(currentUser);
        log.info("User role upgraded to DEALER (Unverified) for: {}", currentUser.getUsername());

        return convertToUserResponse(currentUser);
    }

    /**
     * Search users (admin only)
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(String searchTerm, Pageable pageable) {
        log.info("Searching users with term: {}", searchTerm);

        // Check admin permission
        if (!authService.hasRole(Role.ADMIN)) {
            throw new InsufficientPermissionException("You don't have permission to search users");
        }

        Page<User> users;
        if (StringUtils.hasText(searchTerm)) {
            users = userRepository.searchUsers(searchTerm, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return users.map(this::convertToUserResponse);
    }

    /**
     * Search dealers (accessible by DEALER and ADMIN)
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> searchDealers(String searchTerm, Pageable pageable) {
        log.info("Searching dealers with term: {}", searchTerm);

        User currentUser = authService.getCurrentUser();
        // Allow Dealers (for co-listing) and Admins
        if (!currentUser.hasRole(Role.DEALER) && !currentUser.hasRole(Role.ADMIN)) {
            throw new InsufficientPermissionException("You don't have permission to search dealers");
        }

        if (StringUtils.hasText(searchTerm)) {
            Page<User> dealers = userRepository.searchUsersByRole(searchTerm, Role.DEALER, pageable);
            return dealers.map(this::convertToUserResponse);
        } else {
            // Return all active dealers if no term
            Page<User> dealers = userRepository
                    .findActiveDealersByStatus(com.carselling.oldcar.model.DealerStatus.VERIFIED, pageable);
            return dealers.map(this::convertToUserResponse);
        }
    }

    /**
     * Get all users with filtering (admin only)
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(String username, String email, Role role,
            String location, Boolean isActive, Pageable pageable) {
        log.info("Getting all users with filters");

        // Check admin permission
        if (!authService.hasRole(Role.ADMIN)) {
            throw new InsufficientPermissionException("You don't have permission to view all users");
        }

        Page<User> users = userRepository.findUsersByCriteria(
                username, email, role, location, isActive, pageable);

        return users.map(this::convertToUserResponse);
    }

    /**
     * Get users by role
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsersByRole(Role role, Pageable pageable) {
        log.info("Getting users by role: {}", role);

        // Check admin permission
        if (!authService.hasRole(Role.ADMIN)) {
            throw new InsufficientPermissionException("You don't have permission to view users by role");
        }

        Page<User> users = userRepository.findByRole(role, pageable);
        return users.map(this::convertToUserResponse);
    }

    /**
     * Find user by ID (for internal service use)
     */
    @Transactional(readOnly = true)
    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    /**
     * Get user statistics for admin dashboard
     */
    @Transactional(readOnly = true)
    public UserStatistics getUserStatistics() {
        log.info("Getting user statistics for admin dashboard");

        long totalUsers = userRepository.count();
        // Assuming there are methods to count active, dealer, admin, etc.
        // If not, we might need to add them to UserRepository or use example queries
        long activeUsers = userRepository.countByIsActive(true);
        long dealerCount = userRepository.countByRole(Role.DEALER);
        long adminCount = userRepository.countByRole(Role.ADMIN);

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long newUsersLast7Days = userRepository.countUsersCreatedSince(sevenDaysAgo);

        return UserStatistics.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .dealerCount(dealerCount)
                .adminCount(adminCount)
                .newUsersLast7Days(newUsersLast7Days)
                .build();
    }

    // ... existing constructor ...

    // ... existing methods ...

    /**
     * Upload profile image for current user
     */
    public String uploadProfileImage(org.springframework.web.multipart.MultipartFile file) {
        log.info("Uploading profile image for current user");

        User currentUser = authService.getCurrentUser();

        // Validation handled by FileUploadService

        return uploadProfileImageForUser(currentUser.getId(), file);
    }

    /**
     * Upload profile image for specific user (admin or self)
     */
    public String uploadProfileImageForUser(Long userId, org.springframework.web.multipart.MultipartFile file) {
        log.info("Uploading profile image for user ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Validation handled by FileUploadService

        // Check permission
        User currentUser = authService.getCurrentUser();
        if (!canUpdateUserProfile(currentUser, user)) {
            throw new InsufficientPermissionException("You don't have permission to update this user's profile image");
        }

        try {
            String folder = "users/" + userId + "/profile";

            com.carselling.oldcar.dto.file.FileUploadResponse response = b2FileService.uploadFile(
                    file, folder, currentUser, ResourceType.USER_PROFILE, userId);

            // Store URL in database
            user.setProfileImageUrl(response.getFileUrl());
            userRepository.save(user);

            log.info("Profile image uploaded successfully for user: {}", user.getUsername());

            return response.getFileUrl();
        } catch (java.io.IOException e) {
            log.error("Failed to upload profile image for user: {}", user.getUsername(), e);
            throw new RuntimeException("Failed to upload profile image", e);
        }
    }

    /**
     * Delete profile image for current user
     */
    public void deleteProfileImage() {
        log.info("Deleting profile image for current user");

        User currentUser = authService.getCurrentUser();

        // Optionally delete from storage if needed, but for now just clear DB reference
        // b2FileService.deleteFile(currentUser.getProfileImageUrl());

        currentUser.setProfileImageUrl(null);
        userRepository.save(currentUser);

        log.info("Profile image deleted successfully for user: {}", currentUser.getUsername());
    }

    // Private helper methods

    public UserResponse convertToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .location(user.getLocation())
                .phoneNumber(user.getPhoneNumber())
                .isActive(user.getIsActive())
                .isEmailVerified(user.getIsEmailVerified())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .profileImageUrl(user.getProfileImageUrl())
                // Dealer status fields
                .dealerStatus(user.getDealerStatus() != null ? user.getDealerStatus().name() : null)
                .dealerStatusUpdatedAt(user.getDealerStatusUpdatedAt())
                .dealerStatusReason(user.getDealerStatusReason())
                // Dealer profile fields
                .dealerName(user.getDealerName())
                .showroomName(user.getShowroomName())
                .address(user.getAddress())
                .city(user.getCity())
                .latitude(user.getLatitude())
                .longitude(user.getLongitude())
                .onboardingCompleted(Boolean.TRUE.equals(user.getOnboardingCompleted()))
                .build();
    }

    private boolean canViewUserProfile(User currentUser, User targetUser) {
        // Admin can view any profile
        if (currentUser.hasRole(Role.ADMIN)) {
            return true;
        }

        // Users can view their own profile
        if (currentUser.getId().equals(targetUser.getId())) {
            return true;
        }

        // Restrict to Admin or Self
        return false;
    }

    private boolean canUpdateUserProfile(User currentUser, User targetUser) {
        // Admin can update any profile
        if (currentUser.hasRole(Role.ADMIN)) {
            return true;
        }

        // Users can only update their own profile
        return currentUser.getId().equals(targetUser.getId());
    }

    private boolean canDeleteUserAccount(User currentUser, User targetUser) {
        // Admin can delete any account
        if (currentUser.hasRole(Role.ADMIN)) {
            return true;
        }

        // Users can only delete their own account
        return currentUser.getId().equals(targetUser.getId());
    }

}
