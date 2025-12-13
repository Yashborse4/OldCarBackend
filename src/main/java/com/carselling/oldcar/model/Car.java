package com.carselling.oldcar.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
    @Index(name = "idx_car_featured", columnList = "is_featured")
})
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
    @Pattern(regexp = "^(https?://.*\\.(jpg|jpeg|png|gif|webp))$|^$", 
             message = "Please provide a valid image URL")
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_featured")
    @Builder.Default
    private Boolean isFeatured = false;

    @Column(name = "is_sold")
    @Builder.Default
    private Boolean isSold = false;

    @Column(name = "view_count")
    @Builder.Default
    private Long viewCount = 0L;

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

    @Size(max = 17, message = "VIN must not exceed 17 characters")
    @Pattern(regexp = "^[A-HJ-NPR-Z0-9]{17}$|^$", message = "Please provide a valid VIN")
    @Column(length = 17)
    private String vin;

    @Column(name = "number_of_owners")
    @Min(value = 1, message = "Number of owners must be at least 1")
    @Max(value = 20, message = "Number of owners cannot exceed 20")
    private Integer numberOfOwners;

    @Column(name = "featured_until")
    private LocalDateTime featuredUntil;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_viewed_at")
    private LocalDateTime lastViewedAt;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Size(max = 200, message = "Location must not exceed 200 characters")
    @Column(name = "location", length = 200)
    private String location;

    @ElementCollection
    @CollectionTable(name = "car_images", joinColumns = @JoinColumn(name = "car_id"))
    @Column(name = "image_url")
    @Builder.Default
    private List<String> images = new ArrayList<>();

    @Column(name = "is_available")
    @Builder.Default
    private Boolean isAvailable = true;

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

    public boolean isOwnedBy(User user) {
        return this.owner != null && this.owner.getId().equals(user.getId());
    }

    public boolean isOwnedBy(Long userId) {
        return this.owner != null && this.owner.getId().equals(userId);
    }

    public void incrementViewCount() {
        this.viewCount = this.viewCount == null ? 1 : this.viewCount + 1;
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
}
