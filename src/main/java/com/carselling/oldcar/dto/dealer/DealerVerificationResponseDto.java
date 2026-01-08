package com.carselling.oldcar.dto.dealer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for displaying dealer verification request details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealerVerificationResponseDto {

    private Long id;

    // Dealer info
    private Long dealerId;
    private String dealerUsername;
    private String dealerEmail;
    private String dealerPhone;

    // Business details
    private String businessName;
    private String businessAddress;
    private String gstNumber;
    private String phoneNumber;

    // Location
    private Double latitude;
    private Double longitude;
    private String formattedAddress;

    // Specific images
    private String showroomExteriorImage;
    private String showroomInteriorImage;
    private String visitingCardImage;

    // Additional images (legacy)
    private List<String> showroomImages;

    // Status
    private String status;
    private String statusDisplayName;
    private String adminNotes;

    // Timestamps
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String reviewedByUsername;
}
