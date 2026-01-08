package com.carselling.oldcar.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a dealer verification request.
 * Dealers submit this with showroom images, business details, and location
 * for admin approval.
 */
@Entity
@Table(name = "dealer_verification_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealerVerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dealer_id", nullable = false)
    private User dealer;

    @NotBlank(message = "Business name is required")
    @Size(max = 100)
    @Column(name = "business_name", nullable = false, length = 100)
    private String businessName;

    @NotBlank(message = "Business address is required")
    @Size(max = 500)
    @Column(name = "business_address", nullable = false, length = 500)
    private String businessAddress;

    @Size(max = 20)
    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Size(max = 20)
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Size(max = 500)
    @Column(name = "formatted_address", length = 500)
    private String formattedAddress;

    // Specific required images stored in Firebase
    @NotBlank(message = "Showroom exterior photo is required")
    @Column(name = "showroom_exterior_image", length = 500)
    private String showroomExteriorImage;

    @Column(name = "showroom_interior_image", length = 500)
    private String showroomInteriorImage;

    @Column(name = "visiting_card_image", length = 500)
    private String visitingCardImage;

    // Legacy: additional images (kept for backward compatibility)
    @ElementCollection
    @CollectionTable(name = "dealer_verification_images", joinColumns = @JoinColumn(name = "request_id"))
    @OrderColumn(name = "image_order")
    @Column(name = "image_url")
    @Builder.Default
    private List<String> showroomImages = new ArrayList<>();

    // Declarations
    @Column(name = "info_confirmed", nullable = false)
    @Builder.Default
    private Boolean infoConfirmed = false;

    @Column(name = "terms_accepted", nullable = false)
    @Builder.Default
    private Boolean termsAccepted = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    @Size(max = 500)
    @Column(name = "admin_notes", length = 500)
    private String adminNotes;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    /**
     * Verification status enum
     */
    public enum VerificationStatus {
        PENDING("Pending Review"),
        APPROVED("Approved"),
        REJECTED("Rejected");

        private final String displayName;

        VerificationStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
