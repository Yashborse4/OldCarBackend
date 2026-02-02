package com.carselling.oldcar.controller.admin;

import com.carselling.oldcar.dto.admin.ChangeRoleRequest;
import com.carselling.oldcar.dto.admin.DealerStatusRequest;
import com.carselling.oldcar.dto.admin.ResetPasswordRequest;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.user.UserResponse;
import com.carselling.oldcar.dto.SystemStatistics;
import com.carselling.oldcar.model.UserActivityLog;
import com.carselling.oldcar.model.DealerStatus;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.service.admin.AdminService;
import com.carselling.oldcar.service.user.DealerVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Map;

import com.carselling.oldcar.util.PaginationUtil;

/**
 * Admin Controller for administrative operations
 * Handles user management, role changes, and administrative actions (admin
 * only)
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Validated
@io.swagger.v3.oas.annotations.tags.Tag(name = "Admin Dashboard", description = "Administrative management endpoints")
public class AdminController {

        private final AdminService adminService;
        private final DealerVerificationService dealerVerificationService;

        /**
         * Get all users with pagination and filtering (admin only)
         * GET /api/admin/users
         */
        @GetMapping("/users")
        @io.swagger.v3.oas.annotations.Operation(summary = "Get all users", description = "Retrieve a paginated list of all users")
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
                                                                "Valid roles are: USER, DEALER, ADMIN"));
                        }
                }

                if (roleString != null) {
                        try {
                                role = Role.valueOf(roleString.toUpperCase());
                        } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException(
                                                "Invalid role parameter. Valid roles are: USER, DEALER, ADMIN");
                        }
                }

                Pageable pageable = PaginationUtil.createPageable(page, size, sort, direction);

                Page<UserResponse> users = adminService.getAllUsers(
                                username, email, role, location, isActive, pageable);

                return ResponseEntity.ok(ApiResponse.success(
                                "Users retrieved successfully",
                                String.format("Retrieved %d users with the specified filters.",
                                                users.getTotalElements()),
                                users));
        }

        /**
         * Change user role (admin only)
         * PUT /api/admin/users/{id}/role
         */
        @PutMapping("/users/{id}/role")
        @io.swagger.v3.oas.annotations.Operation(summary = "Update user role", description = "Change the role of a user")
        public ResponseEntity<ApiResponse<UserResponse>> changeUserRole(
                        @PathVariable Long id,
                        @Valid @RequestBody ChangeRoleRequest request) {

                log.info("Admin changing role for user ID: {} to role: {}", id, request.getNewRole());

                UserResponse updatedUser = adminService.changeUserRole(id, request);

                return ResponseEntity.ok(ApiResponse.success(
                                "User role updated successfully",
                                String.format("User role has been changed to %s.", request.getNewRole()),
                                updatedUser));
        }

        /**
         * Verify Dealer (admin only) - LEGACY ENDPOINT
         * POST /api/admin/users/{id}/verify-dealer
         * 
         * @deprecated Use PUT /api/admin/dealers/{id}/status instead
         */
        @Deprecated
        @PostMapping("/users/{id}/verify-dealer")
        public ResponseEntity<ApiResponse<UserResponse>> verifyDealer(
                        @PathVariable Long id,
                        @RequestParam(value = "verified", defaultValue = "true") boolean verified) {

                log.info("Admin verifying dealer ID: {}, status: {} (legacy endpoint)", id, verified);

                UserResponse updatedUser = adminService.verifyDealer(id, verified);

                return ResponseEntity.ok(ApiResponse.success(
                                "Dealer verification updated",
                                verified ? "Dealer has been verified. Their cars will now be visible."
                                                : "Dealer verification revoked. Their cars will be hidden.",
                                updatedUser));
        }

        /**
         * Update dealer status (admin only)
         * PUT /api/admin/dealers/{id}/status
         */
        @PutMapping("/dealers/{id}/status")
        public ResponseEntity<ApiResponse<UserResponse>> updateDealerStatus(
                        @PathVariable Long id,
                        @Valid @RequestBody DealerStatusRequest request) {

                log.info("Admin updating dealer status for ID: {} to: {}", id, request.getStatus());

                UserResponse updatedUser = adminService.updateDealerStatus(id, request);

                String message = switch (request.getStatus()) {
                        case VERIFIED -> "Dealer has been verified. Their cars will now be publicly visible.";
                        case SUSPENDED -> "Dealer has been suspended. Their cars are now hidden.";
                        case REJECTED -> "Dealer has been permanently rejected.";
                        case UNVERIFIED -> "Dealer status set to unverified.";
                };

                return ResponseEntity.ok(ApiResponse.success(
                                "Dealer status updated",
                                message,
                                updatedUser));
        }

        /**
         * Get dealers by status (admin only)
         * GET /api/admin/dealers?status=UNVERIFIED
         */
        @GetMapping("/dealers")
        @io.swagger.v3.oas.annotations.Operation(summary = "Get verification requests", description = "Get pending dealer verification requests")
        public ResponseEntity<ApiResponse<Page<UserResponse>>> getDealersByStatus(
                        @RequestParam(value = "status", required = false) String statusString,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        @RequestParam(value = "sort", defaultValue = "createdAt") String sort,
                        @RequestParam(value = "direction", defaultValue = "desc") String direction) {

                log.info("Admin retrieving dealers with status: {}", statusString);

                DealerStatus status = null;
                if (statusString != null) {
                        try {
                                status = DealerStatus.valueOf(statusString.toUpperCase());
                        } catch (IllegalArgumentException e) {
                                return ResponseEntity.badRequest()
                                                .body(ApiResponse.error(
                                                                "Invalid status parameter",
                                                                "Valid statuses are: UNVERIFIED, VERIFIED, SUSPENDED, REJECTED"));
                        }
                }

                Pageable pageable = PaginationUtil.createPageable(page, size, sort, direction);
                Page<UserResponse> dealers;

                if (status != null) {
                        dealers = adminService.getDealersByStatus(status, pageable);
                } else {
                        // If no status specified, get all dealers
                        dealers = adminService.getAllUsers(null, null, Role.DEALER, null, null, pageable);
                }

                return ResponseEntity.ok(ApiResponse.success(
                                "Dealers retrieved successfully",
                                String.format("Retrieved %d dealers.", dealers.getTotalElements()),
                                dealers));
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
                                bannedUser));
        }

        /**
         * Unban user (admin only)
         * POST /api/admin/users/{id}/unban
         */
        @PostMapping("/users/{id}/unban")
        @io.swagger.v3.oas.annotations.Operation(summary = "Get user details", description = "Get detailed information about a specific user")
        public ResponseEntity<ApiResponse<UserResponse>> unbanUser(@PathVariable Long id) {
                log.info("Admin unbanning user ID: {}", id);

                UserResponse unbannedUser = adminService.banUser(id, false, "Account reactivated by admin");

                return ResponseEntity.ok(ApiResponse.success(
                                "User unbanned successfully",
                                "User account has been reactivated.",
                                unbannedUser));
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
                                hardDelete ? "User has been permanently deleted."
                                                : "User account has been deactivated."));
        }

        /**
         * Get system statistics (admin only)
         * GET /api/admin/statistics
         */
        @GetMapping("/statistics")
        public ResponseEntity<ApiResponse<SystemStatistics>> getSystemStatistics() {
                log.info("Admin retrieving system statistics");

                SystemStatistics statistics = adminService.getSystemStatistics();

                return ResponseEntity.ok(ApiResponse.success(
                                "System statistics retrieved successfully",
                                "Current system statistics have been retrieved.",
                                statistics));
        }

        /**
         * Get user activity logs (admin only)
         * GET /api/admin/users/{id}/activity
         */
        @GetMapping("/users/{id}/activity")
        public ResponseEntity<ApiResponse<List<UserActivityLog>>> getUserActivityLogs(
                        @PathVariable Long id,
                        @RequestParam(value = "startDate", required = false) String startDateString,
                        @RequestParam(value = "endDate", required = false) String endDateString) {

                log.info("Admin retrieving activity logs for user ID: {}", id);

                LocalDateTime startDate;
                LocalDateTime endDate;

                // Date parsing is now cleaner, but we still need to catch format errors here
                // locally or let global handler catch it.
                // Since user requested removal of manual try/catch, we will let
                // DateTimeParseException bubble up.
                // However, GlobalExceptionHandler might not catch strict DateTimeParseException
                // as a BAD_REQUEST unless configured.
                // GlobalHandler has handleIllegalArgumentException but not specifically
                // DateTimeParseException.
                // Let's rely on generic handling or if we want specific message, we'd add it to
                // GlobalHandler.
                // But for standardization, "remove manual try-catch".

                startDate = startDateString != null
                                ? LocalDate.parse(startDateString).atStartOfDay()
                                : LocalDateTime.now().minusDays(30);

                endDate = endDateString != null
                                ? LocalDate.parse(endDateString).atTime(23, 59, 59)
                                : LocalDateTime.now();

                List<UserActivityLog> activityLogs = adminService.getUserActivityLogs(
                                id, startDate, endDate);

                return ResponseEntity.ok(ApiResponse.success(
                                "User activity logs retrieved successfully",
                                String.format("Retrieved %d activity log entries.", activityLogs.size()),
                                activityLogs));
        }

        /**
         * Reset user password (admin only)
         * POST /api/admin/users/{id}/reset-password
         */
        @PostMapping("/users/{id}/reset-password")
        public ResponseEntity<ApiResponse<Object>> resetUserPassword(
                        @PathVariable Long id,
                        @Valid @RequestBody ResetPasswordRequest request) {

                log.info("Admin resetting password for user ID: {}", id);

                adminService.resetUserPassword(id, request.getNewPassword());

                return ResponseEntity.ok(ApiResponse.success(
                                "Password reset successfully",
                                "User password has been reset. Please inform the user of the new password."));
        }

        /**
         * Get user registration statistics (admin only)
         * GET /api/admin/users/registration-stats
         */
        @GetMapping("/users/registration-stats")
        public ResponseEntity<ApiResponse<Map<String, Object>>> getUserRegistrationStats(
                        @RequestParam(value = "days", defaultValue = "30") @Min(1) int days) {

                log.info("Admin retrieving user registration stats for last {} days", days);

                Map<String, Object> stats = adminService.getUserRegistrationStats(days);

                return ResponseEntity.ok(ApiResponse.success(
                                "User registration statistics retrieved successfully",
                                String.format("Registration statistics for the last %d days.", days),
                                stats));
        }

        /**
         * Bulk ban users (admin only)
         * POST /api/admin/users/bulk-ban
         */
        @PostMapping("/users/bulk-ban")
        public ResponseEntity<ApiResponse<Object>> bulkBanUsers(
                        @RequestBody @NotEmpty(message = "User ID list cannot be empty") List<Long> userIds,
                        @RequestParam(value = "reason", defaultValue = "Bulk administrative action") String reason) {

                log.info("Admin performing bulk ban on {} users", userIds.size());

                adminService.bulkUpdateUsers(userIds, "BAN", reason);

                return ResponseEntity.ok(ApiResponse.success(
                                "Bulk ban completed",
                                String.format("Successfully banned %d users.", userIds.size())));
        }

        /**
         * Bulk unban users (admin only)
         * POST /api/admin/users/bulk-unban
         */
        @PostMapping("/users/bulk-unban")
        public ResponseEntity<ApiResponse<Object>> bulkUnbanUsers(
                        @RequestBody @NotEmpty(message = "User ID list cannot be empty") List<Long> userIds) {
                log.info("Admin performing bulk unban on {} users", userIds.size());

                adminService.bulkUpdateUsers(userIds, "UNBAN", null);

                return ResponseEntity.ok(ApiResponse.success(
                                "Bulk unban completed",
                                String.format("Successfully unbanned %d users.", userIds.size())));
        }

        /**
         * Bulk change user role (admin only)
         * POST /api/admin/users/bulk-role-change
         */
        @PostMapping("/users/bulk-role-change")
        public ResponseEntity<ApiResponse<Object>> bulkChangeUserRole(
                        @RequestBody @NotEmpty(message = "User ID list cannot be empty") List<Long> userIds,
                        @RequestParam(value = "newRole") String newRole) {

                log.info("Admin performing bulk role change to {} on {} users", newRole, userIds.size());

                // Validate role
                try {
                        Role.valueOf(newRole.toUpperCase());
                } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException(
                                        "Invalid role parameter. Valid roles are: USER, DEALER, ADMIN");
                }

                adminService.bulkUpdateUsers(userIds, "CHANGE_ROLE", newRole);

                return ResponseEntity.ok(ApiResponse.success(
                                "Bulk role change completed",
                                String.format("Successfully changed role to %s for %d users.", newRole,
                                                userIds.size())));
        }

        /**
         * Get dashboard summary (admin only)
         * GET /api/admin/dashboard
         */
        @GetMapping("/dashboard")
        public ResponseEntity<ApiResponse<com.carselling.oldcar.dto.admin.AdminDashboardResponse>> getDashboardSummary() {
                log.info("Admin retrieving dashboard summary");

                com.carselling.oldcar.dto.admin.AdminDashboardResponse dashboard = adminService.getDashboardResponse();

                return ResponseEntity.ok(ApiResponse.success(
                                "Dashboard summary retrieved successfully",
                                "Admin dashboard data has been compiled.",
                                dashboard));
        }

        // ============ DEALER VERIFICATION REQUESTS ============

        // (Service is injected via constructor)

        /**
         * Get all verification requests with optional status filter
         * GET /api/admin/verification-requests?status=PENDING
         */
        @GetMapping("/verification-requests")
        public ResponseEntity<ApiResponse<Page<com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto>>> getVerificationRequests(
                        @RequestParam(value = "status", required = false) String statusString,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "20") int size,
                        @RequestParam(value = "sort", defaultValue = "submittedAt") String sort,
                        @RequestParam(value = "direction", defaultValue = "desc") String direction) {

                log.info("Admin retrieving verification requests with status: {}", statusString);

                com.carselling.oldcar.model.DealerVerificationRequest.VerificationStatus status = null;
                if (statusString != null) {
                        try {
                                status = com.carselling.oldcar.model.DealerVerificationRequest.VerificationStatus
                                                .valueOf(statusString.toUpperCase());
                        } catch (IllegalArgumentException e) {
                                return ResponseEntity.badRequest()
                                                .body(ApiResponse.error(
                                                                "Invalid status parameter",
                                                                "Valid statuses are: PENDING, APPROVED, REJECTED"));
                        }
                }

                Pageable pageable = PaginationUtil.createPageable(page, size, sort, direction);
                Page<com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto> requests = dealerVerificationService
                                .getRequestsByStatus(status, pageable);

                return ResponseEntity.ok(ApiResponse.success(
                                "Verification requests retrieved successfully",
                                String.format("Retrieved %d verification requests.", requests.getTotalElements()),
                                requests));
        }

        /**
         * Get single verification request by ID
         * GET /api/admin/verification-requests/{id}
         */
        @GetMapping("/verification-requests/{id}")
        public ResponseEntity<ApiResponse<com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto>> getVerificationRequestById(
                        @PathVariable Long id) {

                log.info("Admin retrieving verification request ID: {}", id);

                com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto request = dealerVerificationService
                                .getVerificationRequestById(id);

                return ResponseEntity.ok(ApiResponse.success(
                                "Verification request retrieved successfully",
                                "Request details loaded.",
                                request));
        }

        /**
         * Approve verification request
         * POST /api/admin/verification-requests/{id}/approve
         */
        @PostMapping("/verification-requests/{id}/approve")
        public ResponseEntity<ApiResponse<com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto>> approveVerificationRequest(
                        @PathVariable Long id,
                        org.springframework.security.core.Authentication authentication) {

                log.info("Admin approving verification request ID: {}", id);

                com.carselling.oldcar.model.User admin = (com.carselling.oldcar.model.User) authentication
                                .getPrincipal();
                com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto result = dealerVerificationService
                                .approveVerificationRequest(id, admin.getId());

                return ResponseEntity.ok(ApiResponse.success(
                                "Verification request approved",
                                "Dealer has been verified and can now list cars publicly.",
                                result));
        }

        /**
         * Reject verification request
         * POST /api/admin/verification-requests/{id}/reject
         */
        @PostMapping("/verification-requests/{id}/reject")
        public ResponseEntity<ApiResponse<com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto>> rejectVerificationRequest(
                        @PathVariable Long id,
                        @RequestParam(value = "reason", required = true) String reason,
                        org.springframework.security.core.Authentication authentication) {

                log.info("Admin rejecting verification request ID: {} with reason: {}", id, reason);

                com.carselling.oldcar.model.User admin = (com.carselling.oldcar.model.User) authentication
                                .getPrincipal();
                com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto result = dealerVerificationService
                                .rejectVerificationRequest(id, admin.getId(), reason);

                return ResponseEntity.ok(ApiResponse.success(
                                "Verification request rejected",
                                "Dealer has been notified of the rejection.",
                                result));
        }

        /**
         * Get count of pending verification requests
         * GET /api/admin/verification-requests/pending-count
         */
        @GetMapping("/verification-requests/pending-count")
        public ResponseEntity<ApiResponse<Map<String, Long>>> getPendingVerificationCount() {
                log.info("Admin retrieving pending verification count");

                long count = dealerVerificationService.countPendingRequests();

                return ResponseEntity.ok(ApiResponse.success(
                                "Pending count retrieved",
                                String.format("There are %d pending verification requests.", count),
                                Map.of("pendingCount", count)));
        }
}
