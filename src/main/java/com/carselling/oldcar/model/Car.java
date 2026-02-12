package com.carselling.oldcar.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.BatchSize;

import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Car entity representing car listings in the system
 */
@Entity
@Table(name = "cars", indexes = {
        @Index(name = "idx_car_make", columnList = "make"),
        @Index(name = "idx_car_model", columnList = "model"),
        @Index(name = "idx_car_year", columnList = "year"),
        @Index(name = "idx_car_price", columnList = "price"),
        @Index(name = "idx_car_owner", columnList = "owner_id"),
        @Index(name = "idx_car_created_at", columnList = "created_at"),
        @Index(name = "idx_car_is_active", columnList = "is_active"),
        @Index(name = "idx_car_featured", columnList = "is_featured"),
        @Index(name = "idx_car_retry", columnList = "status, next_retry_at")
})
@SQLRestriction("status <> 'DELETED'")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Car {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Make is required")
    @Size(min = 2, max = 50, message = "Make must be between 2 and 50 characters")
    @Column(nullable = false, length = 50)
    private String make;

    @NotBlank(message = "Model is required")
    @Size(min = 1, max = 100, message = "Model must be between 1 and 100 characters")
    @Column(nullable = false, length = 100)
    private String model;

    @NotNull(message = "Year is required")
    @Min(value = 1900, message = "Year must be after 1900")
    @Max(value = 2030, message = "Year must not exceed 2030")
    @Column(nullable = false)
    private Integer year;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Price cannot exceed 999,999,999.99")
    @Digits(integer = 9, fraction = 2, message = "Price must have at most 9 integer digits and 2 decimal places")
    @Column(nullable = false, precision = 11, scale = 2)
    private BigDecimal price;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    @Column(length = 2000)
    private String description;

    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    @Pattern(regexp = "^$|^(https?://.*\\.(jpg|jpeg|png|gif|webp|JPG|JPEG|PNG|GIF|WEBP)(\\?.*)?)?$", message = "Please provide a valid image URL")
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Optional reference to CarMaster catalog entry.
     * When set, specs can be auto-populated from the catalog.
     * Null means dealer manually entered all specs (car not in catalog).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_master_id", nullable = true)
    private CarMaster carMaster;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "co_owner_id", nullable = true)
    private User coOwner;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_featured")
    @Builder.Default
    private Boolean isFeatured = false;

    @Column(name = "is_sold")
    @Builder.Default
    private Boolean isSold = false;

    @Column(name = "is_approved")
    @Builder.Default
    private Boolean isApproved = false; // For moderation

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private CarStatus status = CarStatus.DRAFT;

    /**
     * Idempotency key for preventing duplicate car creation on retries.
     * Unique combination of owner_id + idempotency_key prevents duplicates.
     */
    @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "view_count")
    @Builder.Default
    private Long viewCount = 0L;

    @Column(name = "inquiry_count")
    @Builder.Default
    private Long inquiryCount = 0L;

    @Column(name = "share_count")
    @Builder.Default
    private Long shareCount = 0L;

    @Column(name = "video_play_count")
    @Builder.Default
    private Long videoPlayCount = 0L;

    @Column(name = "image_swipe_count")
    @Builder.Default
    private Long imageSwipeCount = 0L;

    @Column(name = "contact_click_count")
    @Builder.Default
    private Long contactClickCount = 0L;

    @Column(name = "mileage")
    @Min(value = 0, message = "Mileage cannot be negative")
    @Max(value = 9999999, message = "Mileage cannot exceed 9,999,999")
    private Integer mileage;

    @Size(max = 50, message = "Fuel type must not exceed 50 characters")
    @Column(name = "fuel_type", length = 50)
    private String fuelType;

    @Size(max = 50, message = "Transmission must not exceed 50 characters")
    @Column(length = 50)
    private String transmission;

    @Size(max = 50, message = "Color must not exceed 50 characters")
    @Column(length = 50)
    private String color;

    @Size(max = 20, message = "Registration number must not exceed 20 characters")
    @Column(name = "registration_number", length = 20)
    private String registrationNumber;

    @Column(name = "number_of_owners")
    @Min(value = 1, message = "Number of owners must be at least 1")
    @Max(value = 20, message = "Number of owners cannot exceed 20")
    private Integer numberOfOwners;

    @Column(name = "accident_history")
    private Boolean accidentHistory;

    @Column(name = "repainted_parts")
    private Boolean repaintedParts;

    @Column(name = "engine_issues")
    private Boolean engineIssues;

    @Column(name = "flood_damage")
    private Boolean floodDamage;

    @Column(name = "insurance_claims")
    private Boolean insuranceClaims;

    @Size(max = 100, message = "Variant must not exceed 100 characters")
    @Column(name = "variant", length = 100)
    private String variant;

    @Column(name = "usage_type")
    private String usage;

    @Column(name = "featured_until")
    private LocalDateTime featuredUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "last_viewed_at")
    private LocalDateTime lastViewedAt;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Size(max = 200, message = "Location must not exceed 200 characters")
    @Column(name = "location", length = 200)
    private String location;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "car_images", joinColumns = @JoinColumn(name = "car_id"))
    @OrderColumn(name = "image_order")
    @Column(name = "image_url")
    @BatchSize(size = 50)
    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Column(name = "is_available")
    @Builder.Default
    private Boolean isAvailable = true;

    @Size(max = 500, message = "Video URL must not exceed 500 characters")
    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_status", length = 20)
    @Builder.Default
    private MediaStatus mediaStatus = MediaStatus.INIT;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    // Helper methods for backward compatibility
    public User getUser() {
        return this.owner;
    }

    public void setUser(User user) {
        this.owner = user;
    }

    public Boolean getIsAvailable() {
        return this.isAvailable;
    }

    public void setIsAvailable(Boolean isAvailable) {
        this.isAvailable = isAvailable;
    }

    public Boolean getIsFeatured() {
        return this.isFeatured;
    }

    public void setIsFeatured(Boolean isFeatured) {
        this.isFeatured = isFeatured;
    }

    public Long getViewCount() {
        return (long) (this.viewCount != null ? this.viewCount.intValue() : 0);
    }

    public Long getViewCountLong() {
        return this.viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount != null ? viewCount.longValue() : 0L;
    }

    public void incrementVideoPlayCount() {
        this.videoPlayCount = this.videoPlayCount == null ? 1 : this.videoPlayCount + 1;
    }

    public void incrementImageSwipeCount() {
        this.imageSwipeCount = this.imageSwipeCount == null ? 1 : this.imageSwipeCount + 1;
    }

    public void incrementContactClickCount() {
        this.contactClickCount = this.contactClickCount == null ? 1 : this.contactClickCount + 1;
    }

    public boolean isOwnedBy(User user) {
        return (this.owner != null && this.owner.getId().equals(user.getId())) ||
                (this.coOwner != null && this.coOwner.getId().equals(user.getId()));
    }

    public boolean isOwnedBy(Long userId) {
        return (this.owner != null && this.owner.getId().equals(userId)) ||
                (this.coOwner != null && this.coOwner.getId().equals(userId));
    }

    public void incrementViewCount() {
        this.viewCount = this.viewCount == null ? 1 : this.viewCount + 1;
    }

    public void incrementInquiryCount() {
        this.inquiryCount = this.inquiryCount == null ? 1 : this.inquiryCount + 1;
    }

    public void incrementShareCount() {
        this.shareCount = this.shareCount == null ? 1 : this.shareCount + 1;
    }

    public boolean isCurrentlyFeatured() {
        return Boolean.TRUE.equals(isFeatured) &&
                (featuredUntil == null || featuredUntil.isAfter(LocalDateTime.now()));
    }

    public void setFeatured(boolean featured, int daysToFeature) {
        this.isFeatured = featured;
        if (featured) {
            this.featuredUntil = LocalDateTime.now().plusDays(daysToFeature);
        } else {
            this.featuredUntil = null;
        }
    }

    public String getFullName() {
        return year + " " + make + " " + model;
    }

    public boolean canBeEditedBy(User user) {
        return isOwnedBy(user) || user.hasRole(Role.ADMIN);
    }

    public boolean canBeDeletedBy(User user) {
        return isOwnedBy(user) || user.hasRole(Role.ADMIN);
    }

    public boolean canBeFeaturedBy(User user) {
        return user.hasRole(Role.DEALER) || user.hasRole(Role.ADMIN);
    }

    @PrePersist
    @PreUpdate
    public void sanitizeFields() {
        if (this.imageUrl != null && this.imageUrl.trim().isEmpty()) {
            this.imageUrl = null;
        }
        if (this.videoUrl != null && this.videoUrl.trim().isEmpty()) {
            this.videoUrl = null;
        }
        if (this.mediaStatus == null) {
            this.mediaStatus = MediaStatus.INIT;
        }
    }
}
