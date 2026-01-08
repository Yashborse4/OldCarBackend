package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.Otp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<Otp, Long> {

       // Find active OTP to validate (checking used=false)
       Optional<Otp> findTopByEmailAndPurposeAndUsedFalseOrderByCreatedAtDesc(String email, String purpose);

       // Invalidate all previous active OTPs for this email/purpose
       @org.springframework.data.jpa.repository.Modifying
       @org.springframework.data.jpa.repository.Query("UPDATE Otp o SET o.used = true WHERE o.email = :email AND o.purpose = :purpose AND o.used = false")
       void invalidatePreviousOtps(String email, String purpose);

       // Find latest OTP (used or not) for cooldown check
       Optional<Otp> findTopByEmailAndPurposeOrderByCreatedAtDesc(String email, String purpose);

       // Count OTPs created after a certain time (for rate limiting)
       long countByEmailAndPurposeAndCreatedAtAfter(String email, String purpose, java.time.LocalDateTime timestamp);

       // Find all active OTPs (helper for other logic if needed)
       java.util.List<Otp> findByEmailAndPurposeAndUsedFalse(String email, String purpose);

       // For cleanup jobs later
       void deleteByExpiresAtBefore(java.time.LocalDateTime now);
}
