package com.carselling.oldcar.dto.dealer;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for dealer verification request submission
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealerVerificationRequestDto {

    @NotBlank(message = "Business name is required")
    @Size(max = 100, message = "Business name must not exceed 100 characters")
    private String businessName;

    @NotBlank(message = "Business address is required")
    @Size(max = 500, message = "Business address must not exceed 500 characters")
    private String businessAddress;

    @Size(max = 20, message = "GST number must not exceed 20 characters")
    private String gstNumber;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    @Size(max = 500, message = "Formatted address must not exceed 500 characters")
    private String formattedAddress;

    // Specific required images (Firebase URLs)
    @NotBlank(message = "Showroom exterior photo is required")
    private String showroomExteriorImage;

    private String showroomInteriorImage;

    private String visitingCardImage;

    // Legacy: additional images
    private List<String> showroomImages;

    // Declarations
    @AssertTrue(message = "You must confirm the information is correct")
    private Boolean infoConfirmed;

    @AssertTrue(message = "You must accept the terms and verification policy")
    private Boolean termsAccepted;
}
