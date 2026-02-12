package com.carselling.oldcar.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * User entity representing system users with different roles
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_.@-]+$", message = "Username can only contain letters, numbers, underscores, dots, hyphens, and @")
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 255, message = "Password must be between 8 and 255 characters")
    @Column(nullable = false)
    private String password;

    @Size(max = 50, message = "First name must not exceed 50 characters")
    @Column(name = "first_name", length = 50)
    private String firstName;

    @Size(max = 50, message = "Last name must not exceed 50 characters")
    @Column(name = "last_name", length = 50)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.USER;

    // Dealer status for verification workflow (only relevant for DEALER role)
    @Enumerated(EnumType.STRING)
    @Column(name = "dealer_status", length = 20)
    private DealerStatus dealerStatus;

    @Column(name = "dealer_status_updated_at")
    private LocalDateTime dealerStatusUpdatedAt;

    @Size(max = 500, message = "Dealer status reason must not exceed 500 characters")
    @Column(name = "dealer_status_reason", length = 500)
    private String dealerStatusReason;

    // Dealer profile fields
    @Size(max = 100, message = "Dealer name must not exceed 100 characters")
    @Column(name = "dealer_name", length = 100)
    private String dealerName;

    @Size(max = 100, message = "Showroom name must not exceed 100 characters")
    @Column(name = "showroom_name", length = 100)
    private String showroomName;

    @Size(max = 255, message = "Address must not exceed 255 characters")
    @Column(name = "address", length = 255)
    private String address;

    @Size(max = 100, message = "City must not exceed 100 characters")
    @Column(name = "city", length = 100)
    private String city;

    @Size(max = 100, message = "Location must not exceed 100 characters")
    @Column(length = 100)
    private String location;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "phone_number", length = 20)
    @Pattern(regexp = "^[+]?[1-9]\\d{1,14}$", message = "Please provide a valid phone number")
    private String phoneNumber;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_email_verified")
    @Builder.Default
    private Boolean isEmailVerified = false;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "failed_login_attempts")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "is_account_non_locked")
    @Builder.Default
    private Boolean isAccountNonLocked = true;

    @Column(name = "account_locked_at")
    private LocalDateTime accountLockedAt;

    @Column(name = "last_login_device")
    private String lastLoginDevice;

    @Column(name = "fcm_token", length = 500)
    private String fcmToken; // Firebase Cloud Messaging token for push notifications

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl; // Profile image URL from storage service

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relationships
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Car> cars = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_favorite_cars", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "car_id"))
    @Builder.Default
    private Set<Car> favoriteCars = new HashSet<>();

    // Helper methods
    public boolean isAccountNonLocked() {
        if (isAccountNonLocked != null && !isAccountNonLocked) {
            return false;
        }
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(isActive);
    }

    public boolean hasRole(Role role) {
        return this.role == role;
    }

    public boolean hasPermission(String permission) {
        return this.role.hasPermission(permission);
    }

    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts = (this.failedLoginAttempts == null) ? 1 : this.failedLoginAttempts + 1;
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void lockAccount(int lockDurationMinutes) {
        this.lockedUntil = LocalDateTime.now().plusMinutes(lockDurationMinutes);
    }

    public void setAccountNonLocked(Boolean accountNonLocked) {
        this.isAccountNonLocked = accountNonLocked;
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public String getDisplayName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else {
            return username;
        }
    }

    public String getFullName() {
        return getDisplayName();
    }

    // Dealer status helper methods

    /**
     * Check if this user is a verified dealer.
     * A dealer is considered verified only if they have DEALER role and VERIFIED
     * status.
     */
    public boolean isDealerVerified() {
        return this.role == Role.DEALER && this.dealerStatus == DealerStatus.VERIFIED;
    }

    /**
     * Check if this dealer's listings should be publicly visible.
     * Only VERIFIED dealers have public visibility for their car listings.
     */
    public boolean canListCarsPublicly() {
        if (this.role != Role.DEALER) {
            return true; // Regular users always have public visibility
        }
        return this.dealerStatus != null && this.dealerStatus.isPubliclyVisible();
    }

    /**
     * Check if this dealer can perform business operations (CRUD on listings).
     * UNVERIFIED and VERIFIED dealers can operate, SUSPENDED and REJECTED cannot.
     */
    public boolean canOperateDealerBusiness() {
        if (this.role != Role.DEALER) {
            return true; // Regular users are not restricted
        }
        return this.dealerStatus != null && this.dealerStatus.canOperateBusiness();
    }

    /**
     * Update dealer status with reason and timestamp.
     */
    public void updateDealerStatus(DealerStatus newStatus, String reason) {
        this.dealerStatus = newStatus;
        this.dealerStatusReason = reason;
        this.dealerStatusUpdatedAt = LocalDateTime.now();
    }

    /**
     * @deprecated Use {@link #isDealerVerified()} instead.
     *             Kept for backward compatibility.
     */
    @Deprecated
    public Boolean getVerifiedDealer() {
        return isDealerVerified();
    }

    /**
     * @deprecated Use {@link #updateDealerStatus(DealerStatus, String)} instead.
     *             Kept for backward compatibility.
     */
    @Deprecated
    public void setVerifiedDealer(Boolean verified) {
        if (Boolean.TRUE.equals(verified)) {
            this.dealerStatus = DealerStatus.VERIFIED;
        } else if (this.dealerStatus == DealerStatus.VERIFIED) {
            this.dealerStatus = DealerStatus.UNVERIFIED;
        }
        this.dealerStatusUpdatedAt = LocalDateTime.now();
    }

    // Security method to exclude password from toString
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", role=" + role +
                ", location='" + location + '\'' +
                ", isActive=" + isActive +
                ", createdAt=" + createdAt +
                '}';
    }
}
