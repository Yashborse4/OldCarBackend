package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.admin.ChangeRoleRequest;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.user.UserResponse;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Admin Controller for administrative operations
 * Handles user management, role changes, and administrative actions (admin only)
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    /**
     * Get all users with pagination and filtering (admin only)
     * GET /api/admin/users
     */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "role", required = false) String roleString,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "createdAt") String sort,
            @RequestParam(value = "direction", defaultValue = "desc") String direction) {
        
        log.info("Admin getting all users with filters");

        Role role = null;
        if (roleString != null) {
            try {
                role = Role.valueOf(roleString.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error(
                                "Invalid role parameter",
                                "Valid roles are: VIEWER, SELLER, DEALER, ADMIN"
                        ));
            }
        }

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) 
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<UserResponse> users = adminService.getAllUsers(
                username, email, role, location, isActive, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Users retrieved successfully",
                String.format("Retrieved %d users with the specified filters.", users.getTotalElements()),
                users
        ));
    }

    /**
     * Change user role (admin only)
     * PUT /api/admin/users/{id}/role
     */
    @PutMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse<UserResponse>> changeUserRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request) {
        
        log.info("Admin changing role for user ID: {} to role: {}", id, request.getNewRole());

        UserResponse updatedUser = adminService.changeUserRole(id, request);

        return ResponseEntity.ok(ApiResponse.success(
                "User role updated successfully",
                String.format("User role has been changed to %s.", request.getNewRole()),
                updatedUser
        ));
    }

    /**
     * Ban user (admin only)
     * POST /api/admin/users/{id}/ban
     */
    @PostMapping("/users/{id}/ban")
    public ResponseEntity<ApiResponse<UserResponse>> banUser(
            @PathVariable Long id,
            @RequestParam(value = "reason", defaultValue = "Administrative action") String reason) {
        
        log.info("Admin banning user ID: {} with reason: {}", id, reason);

        UserResponse bannedUser = adminService.banUser(id, true, reason);

        return ResponseEntity.ok(ApiResponse.success(
                "User banned successfully",
                String.format("User has been banned for reason: %s", reason),
                bannedUser
        ));
    }

    /**
     * Unban user (admin only)
     * POST /api/admin/users/{id}/unban
     */
    @PostMapping("/users/{id}/unban")
    public ResponseEntity<ApiResponse<UserResponse>> unbanUser(@PathVariable Long id) {
        log.info("Admin unbanning user ID: {}", id);

        UserResponse unbannedUser = adminService.banUser(id, false, "Account reactivated by admin");

        return ResponseEntity.ok(ApiResponse.success(
                "User unbanned successfully",
                "User account has been reactivated.",
                unbannedUser
        ));
    }

    /**
     * Delete user account (admin only)
     * DELETE /api/admin/users/{id}
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteUser(
            @PathVariable Long id,
            @RequestParam(value = "hard", defaultValue = "false") boolean hardDelete) {
        
        log.info("Admin deleting user ID: {}, hard delete: {}", id, hardDelete);

        adminService.deleteUser(id, hardDelete);

        return ResponseEntity.ok(ApiResponse.success(
                "User deleted successfully",
                hardDelete ? "User has been permanently deleted." : "User account has been deactivated."
        ));
    }

    /**
     * Get system statistics (admin only)
     * GET /api/admin/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<AdminService.SystemStatistics>> getSystemStatistics() {
        log.info("Admin retrieving system statistics");

        AdminService.SystemStatistics statistics = adminService.getSystemStatistics();

        return ResponseEntity.ok(ApiResponse.success(
                "System statistics retrieved successfully",
                "Current system statistics have been retrieved.",
                statistics
        ));
    }

    /**
     * Get user activity logs (admin only)
     * GET /api/admin/users/{id}/activity
     */
    @GetMapping("/users/{id}/activity")
    public ResponseEntity<ApiResponse<List<AdminService.UserActivityLog>>> getUserActivityLogs(
            @PathVariable Long id,
            @RequestParam(value = "startDate", required = false) String startDateString,
            @RequestParam(value = "endDate", required = false) String endDateString) {
        
        log.info("Admin retrieving activity logs for user ID: {}", id);

        // Parse date parameters (simplified for demo - in production use proper date parsing)
        LocalDateTime startDate = startDateString != null ? 
                LocalDateTime.now().minusDays(30) : LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = endDateString != null ? 
                LocalDateTime.now() : LocalDateTime.now();

        List<AdminService.UserActivityLog> activityLogs = adminService.getUserActivityLogs(
                id, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success(
                "User activity logs retrieved successfully",
                String.format("Retrieved %d activity log entries.", activityLogs.size()),
                activityLogs
        ));
    }

    /**
     * Reset user password (admin only)
     * POST /api/admin/users/{id}/reset-password
     */
    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<Object>> resetUserPassword(
            @PathVariable Long id,
            @RequestParam(value = "newPassword") String newPassword) {
        
        log.info("Admin resetting password for user ID: {}", id);

        adminService.resetUserPassword(id, newPassword);

        return ResponseEntity.ok(ApiResponse.success(
                "Password reset successfully",
                "User password has been reset. Please inform the user of the new password."
        ));
    }

    /**
     * Get user registration statistics (admin only)
     * GET /api/admin/users/registration-stats
     */
    @GetMapping("/users/registration-stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserRegistrationStats(
            @RequestParam(value = "days", defaultValue = "30") int days) {
        
        log.info("Admin retrieving user registration stats for last {} days", days);

        Map<String, Object> stats = adminService.getUserRegistrationStats(days);

        return ResponseEntity.ok(ApiResponse.success(
                "User registration statistics retrieved successfully",
                String.format("Registration statistics for the last %d days.", days),
                stats
        ));
    }

    /**
     * Bulk ban users (admin only)
     * POST /api/admin/users/bulk-ban
     */
    @PostMapping("/users/bulk-ban")
    public ResponseEntity<ApiResponse<Object>> bulkBanUsers(
            @RequestBody List<Long> userIds,
            @RequestParam(value = "reason", defaultValue = "Bulk administrative action") String reason) {
        
        log.info("Admin performing bulk ban on {} users", userIds.size());

        adminService.bulkUpdateUsers(userIds, "BAN", reason);

        return ResponseEntity.ok(ApiResponse.success(
                "Bulk ban completed",
                String.format("Successfully banned %d users.", userIds.size())
        ));
    }

    /**
     * Bulk unban users (admin only)
     * POST /api/admin/users/bulk-unban
     */
    @PostMapping("/users/bulk-unban")
    public ResponseEntity<ApiResponse<Object>> bulkUnbanUsers(@RequestBody List<Long> userIds) {
        log.info("Admin performing bulk unban on {} users", userIds.size());

        adminService.bulkUpdateUsers(userIds, "UNBAN", null);

        return ResponseEntity.ok(ApiResponse.success(
                "Bulk unban completed",
                String.format("Successfully unbanned %d users.", userIds.size())
        ));
    }

    /**
     * Bulk change user role (admin only)
     * POST /api/admin/users/bulk-role-change
     */
    @PostMapping("/users/bulk-role-change")
    public ResponseEntity<ApiResponse<Object>> bulkChangeUserRole(
            @RequestBody List<Long> userIds,
            @RequestParam(value = "newRole") String newRole) {
        
        log.info("Admin performing bulk role change to {} on {} users", newRole, userIds.size());

        // Validate role
        try {
            Role.valueOf(newRole.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(
                            "Invalid role parameter",
                            "Valid roles are: VIEWER, SELLER, DEALER, ADMIN"
                    ));
        }

        adminService.bulkUpdateUsers(userIds, "CHANGE_ROLE", newRole);

        return ResponseEntity.ok(ApiResponse.success(
                "Bulk role change completed",
                String.format("Successfully changed role to %s for %d users.", newRole, userIds.size())
        ));
    }

    /**
     * Get dashboard summary (admin only)
     * GET /api/admin/dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardSummary() {
        log.info("Admin retrieving dashboard summary");

        AdminService.SystemStatistics stats = adminService.getSystemStatistics();

        Map<String, Object> dashboard = Map.of(
                "systemStatistics", stats,
                "quickActions", List.of(
                        Map.of("title", "User Management", "icon", "users", "link", "/admin/users"),
                        Map.of("title", "Car Management", "icon", "car", "link", "/admin/cars"),
                        Map.of("title", "System Statistics", "icon", "chart", "link", "/admin/statistics"),
                        Map.of("title", "Activity Logs", "icon", "activity", "link", "/admin/logs")
                ),
                "recentActivity", List.of(
                        Map.of("action", "User Registration", "count", stats.getUserStatistics().getNewUsersLast7Days(), "period", "Last 7 days"),
                        Map.of("action", "Cars Listed", "count", stats.getCarStatistics().getNewCarsLast7Days(), "period", "Last 7 days"),
                        Map.of("action", "Total Users", "count", stats.getUserStatistics().getTotalUsers(), "period", "Current"),
                        Map.of("action", "Total Cars", "count", stats.getCarStatistics().getTotalCars(), "period", "Current")
                )
        );

        return ResponseEntity.ok(ApiResponse.success(
                "Dashboard summary retrieved successfully",
                "Admin dashboard data has been compiled.",
                dashboard
        ));
    }
}
