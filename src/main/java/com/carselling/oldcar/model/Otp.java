package com.carselling.oldcar.model;

import jakarta.persistence.*;


import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for storing One-Time Passwords (OTPs) securely
 */
@Entity
@Table(name = "otps", indexes = {
        @Index(name = "idx_otp_email_purpose", columnList = "email, purpose")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Otp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "username", nullable = true)
    private String username;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Hashed OTP for secure storage
     * This is the ONLY field that should be persisted for security
     */
    @NotBlank
    @Column(name = "otp_hash", nullable = false, length = 255)
    private String otpHash;

    /**
     * Plain OTP code - DEPRECATED, should not be stored
     * Kept as nullable for backward compatibility during migration
     * 
     * @deprecated Use otpHash instead for security
     */
    @Deprecated
    @Column(name = "otp_code", nullable = true, length = 10)
    private String otpCode;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String purpose; // EMAIL_VERIFICATION, PASSWORD_RESET

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private Integer attempts = 0;

    @Builder.Default
    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts = 5;

    @Builder.Default
    @Column(nullable = false)
    private Boolean used = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isBlocked() {
        return attempts >= maxAttempts;
    }

    public void incrementAttempts() {
        this.attempts++;
    }
}
