package com.carselling.oldcar.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * OTP (One Time Password) entity for secure password reset functionality
 */
@Entity
@Table(name = "otps", indexes = {
    @Index(name = "idx_otp_username", columnList = "username"),
    @Index(name = "idx_otp_code", columnList = "otp_code"),
    @Index(name = "idx_otp_expires_at", columnList = "expires_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Otp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Column(nullable = false, length = 50)
    private String username;

    @NotBlank(message = "OTP code is required")
    @Size(min = 5, max = 6, message = "OTP code must be 5-6 digits")
    @Column(name = "otp_code", nullable = false, length = 6)
    private String otpCode;

    @NotNull(message = "Expiration time is required")
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_used")
    @Builder.Default
    private Boolean isUsed = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Helper methods
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !Boolean.TRUE.equals(isUsed) && !isExpired();
    }

    public void markAsUsed() {
        this.isUsed = true;
        this.usedAt = LocalDateTime.now();
    }
}
