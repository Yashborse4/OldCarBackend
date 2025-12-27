package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.admin.ChangeRoleRequest;
import com.carselling.oldcar.dto.user.UserResponse;
import com.carselling.oldcar.dto.SystemStatistics;
import com.carselling.oldcar.exception.InsufficientPermissionException;
import com.carselling.oldcar.exception.InvalidInputException;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.Builder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Admin Service for administrative operations
 * Handles user management, role changes, and administrative actions
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final AuthService authService;
    private final UserService userService;
    private final CarService carService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * Get all users with pagination and filtering (admin only)
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(String username, String email, Role role,
            String location, Boolean isActive, Pageable pageable) {
        log.info("Admin retrieving all users with filters");

        // Check admin permission
        ensureAdminPermission();

        return userService.getAllUsers(username, email, role, location, isActive, pageable);
    }

    /**
     * Change user role (admin only)
     */
    public UserResponse changeUserRole(Long userId, ChangeRoleRequest request) {
        log.info("Admin changing role for user ID: {} to role: {}", userId, request.getNewRole());

        // Check admin permission
        ensureAdminPermission();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        User currentAdmin = authService.getCurrentUser();

        // Prevent admin from changing their own role
        if (user.getId().equals(currentAdmin.getId())) {
            throw new InvalidInputException("You cannot change your own role");
        }

        Role newRole;
        try {
            newRole = Role.valueOf(request.getNewRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid role: " + request.getNewRole());
        }

        // Prevent creating multiple admin accounts (optional business rule)
        if (newRole == Role.ADMIN) {
            long adminCount = userRepository.countByRole(Role.ADMIN);
            if (adminCount >= 5) { // Limit to 5 admins
                throw new InvalidInputException("Maximum number of admin accounts reached");
            }
        }

        Role oldRole = user.getRole();
        user.setRole(newRole);

        // Auto-verify if promoted to DEALER - DISABLED per new requirement
        // Users promoted to DEALER must be manually verified.

        // If demoted to USER, ensure verifiedDealer is false
        if (newRole == Role.USER) {
            user.setVerifiedDealer(false);
        }

        user = userRepository.save(user);

        log.info("User role changed successfully for user: {} from {} to {} by admin: {}",
                user.getUsername(), oldRole, newRole, currentAdmin.getUsername());

        if (newRole == Role.DEALER) {
            // Publish event for email/notification
            eventPublisher.publishEvent(new com.carselling.oldcar.event.DealerUpgradedEvent(this, user));
        }

        return userService.getUserProfile(user.getId());
    }

    /**
     * Verify/Unverify a Dealer (admin only)
     */
    public UserResponse verifyDealer(Long userId, boolean verified) {
        log.info("Admin setting dealer verification for user ID: {} to: {}", userId, verified);

        // Check admin permission
        ensureAdminPermission();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Ensure user is actually a dealer
        if (user.getRole() != Role.DEALER) {
            throw new InvalidInputException("User is not a dealer. Only dealers can be verified.");
        }

        user.setVerifiedDealer(verified);
        user = userRepository.save(user);

        log.info("Dealer verification updated: {} for user: {}", verified, user.getUsername());

        // Publish event for notification
        eventPublisher.publishEvent(new com.carselling.oldcar.event.DealerVerifiedEvent(this, user, verified));

        return userService.getUserProfile(user.getId());
    }

    /**
     * Ban/Unban user (admin only)
     */
    public UserResponse banUser(Long userId, boolean ban, String reason) {
        log.info("Admin {} user ID: {}, reason: {}", ban ? "banning" : "unbanning", userId, reason);

        // Check admin permission
        ensureAdminPermission();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        User currentAdmin = authService.getCurrentUser();

        // Prevent admin from banning themselves
        if (user.getId().equals(currentAdmin.getId())) {
            throw new InvalidInputException("You cannot ban yourself");
        }

        // Prevent banning other admins
        if (user.hasRole(Role.ADMIN)) {
            throw new InvalidInputException("You cannot ban other administrators");
        }

        user.setIsActive(!ban);

        if (ban) {
            // When banning, also lock the account and reset failed attempts
            user.lockAccount(Integer.MAX_VALUE); // Effectively permanent lock
            log.info("User banned: {} by admin: {} for reason: {}",
                    user.getUsername(), currentAdmin.getUsername(), reason);
        } else {
            // When unbanning, unlock account and reset failed attempts
            user.resetFailedLoginAttempts();
            log.info("User unbanned: {} by admin: {}",
                    user.getUsername(), currentAdmin.getUsername());
        }

        user = userRepository.save(user);
        return userService.getUserProfile(user.getId());
    }

    /**
     * Delete user account permanently (admin only)
     */
    public void deleteUser(Long userId, boolean hardDelete) {
        log.info("Admin deleting user ID: {}, hardDelete: {}", userId, hardDelete);

        // Check admin permission
        ensureAdminPermission();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        User currentAdmin = authService.getCurrentUser();

        // Prevent admin from deleting themselves
        if (user.getId().equals(currentAdmin.getId())) {
            throw new InvalidInputException("You cannot delete your own account");
        }

        // Prevent deleting other admins
        if (user.hasRole(Role.ADMIN)) {
            throw new InvalidInputException("You cannot delete other administrators");
        }

        try {
            if (hardDelete) {
                // Hard delete - remove from database completely
                // First, soft delete all user's cars
                // Note: In a real application, you might want to transfer ownership or handle
                // this differently
                userRepository.delete(user);
                log.info("User hard deleted: {} by admin: {}", user.getUsername(), currentAdmin.getUsername());
            } else {
                // Soft delete - deactivate account
                user.setIsActive(false);
                userRepository.save(user);
                log.info("User soft deleted: {} by admin: {}", user.getUsername(), currentAdmin.getUsername());
            }
        } catch (Exception e) {
            log.error("Error deleting user: {} by admin: {}", user.getUsername(), currentAdmin.getUsername(), e);
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    /**
     * Get system statistics (admin only)
     */
    @Transactional(readOnly = true)
    public SystemStatistics getSystemStatistics() {
        log.info("Admin retrieving system statistics");

        // Check admin permission
        ensureAdminPermission();

        UserService.UserStatistics userStats = userService.getUserStatistics();
        CarService.CarStatistics carStats = carService.getCarStatistics();

        return SystemStatistics.builder()
                .userStatistics(userStats)
                .carStatistics(carStats)
                .build();
    }

    /**
     * Get user activity logs (admin only)
     */
    @Transactional(readOnly = true)
    public List<UserActivityLog> getUserActivityLogs(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Admin retrieving activity logs for user ID: {}", userId);

        // Check admin permission
        ensureAdminPermission();

        // In a real application, you would have an activity log table
        // For now, return an empty list until implemented
        return List.of();
    }

    /**
     * Reset user password (admin only)
     */
    public void resetUserPassword(Long userId, String newPassword) {
        log.info("Admin resetting password for user ID: {}", userId);

        // Check admin permission
        ensureAdminPermission();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        User currentAdmin = authService.getCurrentUser();

        // Encode and set new password
        user.setPassword(newPassword); // This should be encoded in the service layer
        // Reset failed login attempts
        user.resetFailedLoginAttempts();

        userRepository.save(user);

        log.info("Password reset for user: {} by admin: {}", user.getUsername(), currentAdmin.getUsername());
    }

    /**
     * Get user registration statistics (admin only)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUserRegistrationStats(int days) {
        log.info("Admin retrieving user registration stats for last {} days", days);

        // Check admin permission
        ensureAdminPermission();

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<Object[]> stats = userRepository.getUserRegistrationStats(startDate);

        // Transform data for frontend consumption
        // This would typically return a structured format for charts/graphs
        return Map.of(
                "period", days + " days",
                "data", stats,
                "totalNewUsers", userRepository.countUsersCreatedSince(startDate));
    }

    /**
     * Bulk operations on users (admin only)
     */
    public void bulkUpdateUsers(List<Long> userIds, String action, Object parameter) {
        log.info("Admin performing bulk action: {} on {} users", action, userIds.size());

        // Check admin permission
        ensureAdminPermission();

        User currentAdmin = authService.getCurrentUser();

        switch (action.toUpperCase()) {
            case "BAN" -> {
                for (Long userId : userIds) {
                    if (!userId.equals(currentAdmin.getId())) {
                        banUser(userId, true, "Bulk ban operation");
                    }
                }
            }
            case "UNBAN" -> {
                for (Long userId : userIds) {
                    banUser(userId, false, "Bulk unban operation");
                }
            }
            case "CHANGE_ROLE" -> {
                String newRole = (String) parameter;
                ChangeRoleRequest roleRequest = ChangeRoleRequest.builder()
                        .newRole(newRole)
                        .build();
                for (Long userId : userIds) {
                    if (!userId.equals(currentAdmin.getId())) {
                        changeUserRole(userId, roleRequest);
                    }
                }
            }
            default -> throw new InvalidInputException("Invalid bulk action: " + action);
        }

        log.info("Bulk operation {} completed by admin: {}", action, currentAdmin.getUsername());
    }

    // Private helper methods

    private void ensureAdminPermission() {
        if (!authService.hasRole(Role.ADMIN)) {
            throw new InsufficientPermissionException("You must be an administrator to perform this action");
        }
    }

    private String getSystemUptime() {
        // This would typically return actual system uptime
        // For now, return a placeholder
        return "72 hours, 15 minutes";
    }

    // Inner classes for response DTOs

    @Data
    @Builder
    public static class UserActivityLog {
        private Long userId;
        private String action;
        private LocalDateTime timestamp;
        private String ipAddress;
        private String userAgent;
        private String details;
    }
}
