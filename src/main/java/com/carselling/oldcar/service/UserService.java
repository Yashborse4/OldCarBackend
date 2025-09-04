package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.user.UpdateUserRequest;
import com.carselling.oldcar.dto.user.UserResponse;
import com.carselling.oldcar.dto.user.UserSummary;
import com.carselling.oldcar.exception.InsufficientPermissionException;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

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

    /**
     * Update user profile
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

        if (StringUtils.hasText(request.getEmail()) && !request.getEmail().equals(user.getEmail())) {
            // Check if email is already in use
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email is already in use by another user");
            }
            user.setEmail(request.getEmail());
            user.setIsEmailVerified(false); // Reset email verification status
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

        if (StringUtils.hasText(request.getPhoneNumber()) && !request.getPhoneNumber().equals(user.getPhoneNumber())) {
            user.setPhoneNumber(request.getPhoneNumber());
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
     * Get user statistics (admin only)
     */
    @Transactional(readOnly = true)
    public UserStatistics getUserStatistics() {
        log.info("Getting user statistics");

        // Check admin permission
        if (!authService.hasRole(Role.ADMIN)) {
            throw new InsufficientPermissionException("You don't have permission to view user statistics");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);
        LocalDateTime sevenDaysAgo = now.minusDays(7);

        return UserStatistics.builder()
                .totalUsers(userRepository.count())
                .activeUsers(userRepository.countByIsActive(true))
                .inactiveUsers(userRepository.countByIsActive(false))
                .viewerCount(userRepository.countByRole(Role.VIEWER))
                .sellerCount(userRepository.countByRole(Role.SELLER))
                .dealerCount(userRepository.countByRole(Role.DEALER))
                .adminCount(userRepository.countByRole(Role.ADMIN))
                .newUsersLast30Days(userRepository.countUsersCreatedSince(thirtyDaysAgo))
                .newUsersLast7Days(userRepository.countUsersCreatedSince(sevenDaysAgo))
                .build();
    }

    /**
     * Convert User to UserSummary (for nested responses)
     */
    public UserSummary convertToUserSummary(User user) {
        if (user == null) return null;

        return UserSummary.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole().name())
                .location(user.getLocation())
                .build();
    }

    // Private helper methods

    private UserResponse convertToUserResponse(User user) {
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

        // For now, users can view other users' basic profiles
        // In a real app, you might want more restrictive permissions
        return true;
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

    // Inner class for user statistics
    @lombok.Data
    @lombok.Builder
    public static class UserStatistics {
        private long totalUsers;
        private long activeUsers;
        private long inactiveUsers;
        private long viewerCount;
        private long sellerCount;
        private long dealerCount;
        private long adminCount;
        private long newUsersLast30Days;
        private long newUsersLast7Days;
    }
}
