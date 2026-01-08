package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.DealerStatus;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * User Repository with custom query methods and optimization
 * Provides efficient data access methods for user management
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

       // Basic finder methods
       Optional<User> findByUsername(String username);

       Optional<User> findByEmail(String email);

       Optional<User> findByUsernameOrEmail(String username, String email);

       // Existence check methods (more efficient than finder methods for boolean
       // checks)
       boolean existsByUsername(String username);

       boolean existsByEmail(String email);

       boolean existsByUsernameOrEmail(String username, String email);

       // Find users by role
       List<User> findByRole(Role role);

       Page<User> findByRole(Role role, Pageable pageable);

       // Find active users
       @Query("SELECT u FROM User u WHERE u.isActive = true")
       List<User> findAllActiveUsers();

       @Query("SELECT u FROM User u WHERE u.isActive = true")
       Page<User> findAllActiveUsers(Pageable pageable);

       // Find users by role and active status
       @Query("SELECT u FROM User u WHERE u.role = :role AND u.isActive = true")
       List<User> findActiveUsersByRole(@Param("role") Role role);

       @Query("SELECT u FROM User u WHERE u.role = :role AND u.isActive = true")
       Page<User> findActiveUsersByRole(@Param("role") Role role, Pageable pageable);

       // Find users by location
       List<User> findByLocationContainingIgnoreCase(String location);

       Page<User> findByLocationContainingIgnoreCase(String location, Pageable pageable);

       // Find users created within date range
       @Query("SELECT u FROM User u WHERE u.createdAt BETWEEN :startDate AND :endDate")
       List<User> findUsersCreatedBetween(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       @Query("SELECT u FROM User u WHERE u.createdAt BETWEEN :startDate AND :endDate")
       Page<User> findUsersCreatedBetween(@Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate,
                     Pageable pageable);

       // Find users with failed login attempts
       @Query("SELECT u FROM User u WHERE u.failedLoginAttempts >= :threshold")
       List<User> findUsersWithFailedAttempts(@Param("threshold") Integer threshold);

       // Find locked users
       @Query("SELECT u FROM User u WHERE u.lockedUntil IS NOT NULL AND u.lockedUntil > CURRENT_TIMESTAMP")
       List<User> findLockedUsers();

       // Find users by email verification status
       List<User> findByIsEmailVerified(Boolean isEmailVerified);

       Page<User> findByIsEmailVerified(Boolean isEmailVerified, Pageable pageable);

       // Search users by username or email (case insensitive)
       @Query("SELECT u FROM User u WHERE " +
                     "LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                     "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
       List<User> searchUsers(@Param("searchTerm") String searchTerm);

       @Query("SELECT u FROM User u WHERE " +
                     "LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
                     "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
       Page<User> searchUsers(@Param("searchTerm") String searchTerm, Pageable pageable);

       // Advanced search with multiple criteria
       @Query("SELECT u FROM User u WHERE " +
                     "(:username IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%'))) AND " +
                     "(:email IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
                     "(:role IS NULL OR u.role = :role) AND " +
                     "(:location IS NULL OR LOWER(u.location) LIKE LOWER(CONCAT('%', :location, '%'))) AND " +
                     "(:isActive IS NULL OR u.isActive = :isActive)")
       Page<User> findUsersByCriteria(@Param("username") String username,
                     @Param("email") String email,
                     @Param("role") Role role,
                     @Param("location") String location,
                     @Param("isActive") Boolean isActive,
                     Pageable pageable);

       // Count methods for statistics
       long countByRole(Role role);

       long countByIsActive(Boolean isActive);

       @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :date")
       long countUsersCreatedSince(@Param("date") LocalDateTime date);

       // Update methods
       @Modifying
       @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = NULL WHERE u.id = :userId")
       int resetFailedLoginAttempts(@Param("userId") Long userId);

       @Modifying
       @Query("UPDATE User u SET u.lastLoginAt = CURRENT_TIMESTAMP WHERE u.id = :userId")
       int updateLastLogin(@Param("userId") Long userId);

       @Modifying
       @Query("UPDATE User u SET u.isActive = :isActive WHERE u.id = :userId")
       int updateUserActiveStatus(@Param("userId") Long userId, @Param("isActive") Boolean isActive);

       @Modifying
       @Query("UPDATE User u SET u.role = :role WHERE u.id = :userId")
       int updateUserRole(@Param("userId") Long userId, @Param("role") Role role);

       @Modifying
       @Query("UPDATE User u SET u.isEmailVerified = true WHERE u.id = :userId")
       int markEmailAsVerified(@Param("userId") Long userId);

       @Modifying
       @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :userId")
       int incrementFailedLoginAttempts(@Param("userId") Long userId);

       @Modifying
       @Query("UPDATE User u SET u.lockedUntil = :lockedUntil WHERE u.id = :userId")
       int lockUser(@Param("userId") Long userId, @Param("lockedUntil") LocalDateTime lockedUntil);

       // Delete methods
       @Modifying
       @Query("DELETE FROM User u WHERE u.isActive = false AND u.createdAt < :cutoffDate")
       int deleteInactiveUsersOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

       // Statistics queries for admin dashboard
       @Query("SELECT u.role, COUNT(u) FROM User u GROUP BY u.role")
       List<Object[]> getUserCountByRole();

       @Query("SELECT DATE(u.createdAt), COUNT(u) FROM User u " +
                     "WHERE u.createdAt >= :startDate " +
                     "GROUP BY DATE(u.createdAt) " +
                     "ORDER BY DATE(u.createdAt)")
       List<Object[]> getUserRegistrationStats(@Param("startDate") LocalDateTime startDate);

       // Find users with cars (for cleanup operations)
       @Query("SELECT DISTINCT u FROM User u INNER JOIN u.cars c WHERE c.isActive = true")
       List<User> findUsersWithActiveCars();

       @Query("SELECT u FROM User u WHERE NOT EXISTS (SELECT c FROM Car c WHERE c.owner = u AND c.isActive = true)")
       List<User> findUsersWithoutActiveCars();

       // ==================== DEALER STATUS QUERIES ====================

       // Find dealers by status
       @Query("SELECT u FROM User u WHERE u.role = com.carselling.oldcar.model.Role.DEALER AND u.dealerStatus = :status")
       List<User> findDealersByStatus(@Param("status") DealerStatus status);

       @Query("SELECT u FROM User u WHERE u.role = com.carselling.oldcar.model.Role.DEALER AND u.dealerStatus = :status")
       Page<User> findDealersByStatus(@Param("status") DealerStatus status, Pageable pageable);

       // Find active dealers by status
       @Query("SELECT u FROM User u WHERE u.role = com.carselling.oldcar.model.Role.DEALER AND u.dealerStatus = :status AND u.isActive = true")
       List<User> findActiveDealersByStatus(@Param("status") DealerStatus status);

       @Query("SELECT u FROM User u WHERE u.role = com.carselling.oldcar.model.Role.DEALER AND u.dealerStatus = :status AND u.isActive = true")
       Page<User> findActiveDealersByStatus(@Param("status") DealerStatus status, Pageable pageable);

       // Count dealers by status
       @Query("SELECT COUNT(u) FROM User u WHERE u.role = com.carselling.oldcar.model.Role.DEALER AND u.dealerStatus = :status")
       long countDealersByStatus(@Param("status") DealerStatus status);

       // Count dealers by status grouped
       @Query("SELECT u.dealerStatus, COUNT(u) FROM User u WHERE u.role = com.carselling.oldcar.model.Role.DEALER GROUP BY u.dealerStatus")
       List<Object[]> getDealerCountByStatus();

       // Find verified dealers (for public car queries)
       @Query("SELECT u FROM User u WHERE u.role = com.carselling.oldcar.model.Role.DEALER AND u.dealerStatus = com.carselling.oldcar.model.DealerStatus.VERIFIED AND u.isActive = true")
       List<User> findAllVerifiedDealers();

       // Update dealer status
       @Modifying
       @Query("UPDATE User u SET u.dealerStatus = :status, u.dealerStatusReason = :reason, u.dealerStatusUpdatedAt = CURRENT_TIMESTAMP WHERE u.id = :userId")
       int updateDealerStatus(@Param("userId") Long userId, @Param("status") DealerStatus status,
                     @Param("reason") String reason);
}
