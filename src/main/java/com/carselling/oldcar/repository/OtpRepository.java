package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.Otp;
import com.carselling.oldcar.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * OTP Repository for secure password reset functionality
 * Provides efficient data access methods for OTP management
 */
@Repository
public interface OtpRepository extends JpaRepository<Otp, Long> {

    // Find OTP by username and code
    Optional<Otp> findByUsernameAndOtpCode(String username, String otpCode);
    
    // Find OTP by User and OTP value and not used
    Optional<Otp> findByUserAndOtpCodeAndIsUsedFalse(User user, String otpCode);
    
    // Alias method for backward compatibility
    default Optional<Otp> findByUserAndOtpValueAndIsUsedFalse(User user, String otpValue) {
        return findByUserAndOtpCodeAndIsUsedFalse(user, otpValue);
    }

    // Find valid OTP (not used and not expired)
    @Query("SELECT o FROM Otp o WHERE o.username = :username AND o.otpCode = :otpCode " +
           "AND o.isUsed = false AND o.expiresAt > CURRENT_TIMESTAMP")
    Optional<Otp> findValidOtpByUsernameAndCode(@Param("username") String username, 
                                               @Param("otpCode") String otpCode);

    // Find all OTPs for a username
    List<Otp> findByUsername(String username);

    // Find all valid OTPs for a username
    @Query("SELECT o FROM Otp o WHERE o.username = :username " +
           "AND o.isUsed = false AND o.expiresAt > CURRENT_TIMESTAMP")
    List<Otp> findValidOtpsByUsername(@Param("username") String username);

    // Find the latest OTP for a username
    Optional<Otp> findTopByUsernameOrderByCreatedAtDesc(String username);

    // Find the latest valid OTP for a username
    @Query("SELECT o FROM Otp o WHERE o.username = :username " +
           "AND o.isUsed = false AND o.expiresAt > CURRENT_TIMESTAMP " +
           "ORDER BY o.createdAt DESC")
    Optional<Otp> findLatestValidOtpByUsername(@Param("username") String username);

    // Find expired OTPs
    @Query("SELECT o FROM Otp o WHERE o.expiresAt < CURRENT_TIMESTAMP")
    List<Otp> findExpiredOtps();

    // Find used OTPs
    List<Otp> findByIsUsed(Boolean isUsed);

    // Find OTPs created within date range
    @Query("SELECT o FROM Otp o WHERE o.createdAt BETWEEN :startDate AND :endDate")
    List<Otp> findOtpsCreatedBetween(@Param("startDate") LocalDateTime startDate, 
                                    @Param("endDate") LocalDateTime endDate);

    // Count OTPs for a username within a time period (for rate limiting)
    @Query("SELECT COUNT(o) FROM Otp o WHERE o.username = :username " +
           "AND o.createdAt >= :since")
    long countOtpsCreatedByUsernameSince(@Param("username") String username, 
                                        @Param("since") LocalDateTime since);

    // Check if user has valid OTP
    @Query("SELECT COUNT(o) > 0 FROM Otp o WHERE o.username = :username " +
           "AND o.isUsed = false AND o.expiresAt > CURRENT_TIMESTAMP")
    boolean hasValidOtp(@Param("username") String username);

    // Update methods
    @Modifying
    @Query("UPDATE Otp o SET o.isUsed = true, o.usedAt = CURRENT_TIMESTAMP WHERE o.id = :otpId")
    int markOtpAsUsed(@Param("otpId") Long otpId);

    @Modifying
    @Query("UPDATE Otp o SET o.isUsed = true, o.usedAt = CURRENT_TIMESTAMP " +
           "WHERE o.username = :username AND o.otpCode = :otpCode")
    int markOtpAsUsedByUsernameAndCode(@Param("username") String username, 
                                      @Param("otpCode") String otpCode);

    @Modifying
    @Query("UPDATE Otp o SET o.isUsed = true, o.usedAt = CURRENT_TIMESTAMP " +
           "WHERE o.username = :username AND o.isUsed = false")
    int markAllOtpsAsUsedByUsername(@Param("username") String username);

    // Delete methods for cleanup
    @Modifying
    @Query("DELETE FROM Otp o WHERE o.expiresAt < :cutoffDate")
    int deleteExpiredOtpsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("DELETE FROM Otp o WHERE o.isUsed = true AND o.usedAt < :cutoffDate")
    int deleteUsedOtpsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("DELETE FROM Otp o WHERE o.createdAt < :cutoffDate")
    int deleteOtpsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    // Statistics methods
    @Query("SELECT COUNT(o) FROM Otp o WHERE o.createdAt >= :date")
    long countOtpsCreatedSince(@Param("date") LocalDateTime date);

    @Query("SELECT COUNT(o) FROM Otp o WHERE o.isUsed = true AND o.usedAt >= :date")
    long countOtpsUsedSince(@Param("date") LocalDateTime date);

    @Query("SELECT o.username, COUNT(o) FROM Otp o WHERE o.createdAt >= :startDate " +
           "GROUP BY o.username ORDER BY COUNT(o) DESC")
    List<Object[]> getOtpUsageStatsByUser(@Param("startDate") LocalDateTime startDate);
}
