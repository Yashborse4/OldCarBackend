package com.carselling.oldcar.dto.car;

import com.carselling.oldcar.model.Enquiry;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnquiryRequestDto {

    @NotNull(message = "Car ID is required")
    private Long carId;

    @NotNull(message = "Enquiry type is required")
    private Enquiry.EnquiryType type;

    private String message;

    private String preferredTimeSlot; // e.g., "MORNING", "AFTER_6PM"

    private LocalDateTime scheduledTime;

    @Pattern(regexp = "^\\d{10}$", message = "Mobile number must be exactly 10 digits")
    private String contactNumber;
}
