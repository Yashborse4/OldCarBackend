package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.EnquiryRequestDto;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.model.Enquiry;
import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.car.EnquiryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/enquiries")
@RequiredArgsConstructor
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "Enquiry", description = "Car inquiry and lead management")
public class EnquiryController {

    private final EnquiryService enquiryService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Enquiry>> createEnquiry(
            @Valid @RequestBody EnquiryRequestDto request,
            Authentication authentication) {
        
        Long userId = ((UserPrincipal) authentication.getPrincipal()).getId();
        log.info("Received enquiry request from user {} for car {}", userId, request.getCarId());
        
        Enquiry enquiry = enquiryService.createEnquiry(
                userId,
                request.getCarId(),
                request.getType(),
                request.getMessage(),
                request.getPreferredTimeSlot(),
                request.getScheduledTime(),
                request.getContactNumber()
        );
        
        return ResponseEntity.ok(ApiResponse.success("Enquiry submitted successfully", enquiry));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Page<Enquiry>>> getMyEnquiries(
            Pageable pageable,
            Authentication authentication) {
        
        Long userId = ((UserPrincipal) authentication.getPrincipal()).getId();
        Page<Enquiry> enquiries = enquiryService.getUserEnquiries(userId, pageable);
        
        return ResponseEntity.ok(ApiResponse.success("My enquiries retrieved", enquiries));
    }

    @GetMapping("/dealer")
    @PreAuthorize("hasAnyRole('DEALER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<Enquiry>>> getDealerEnquiries(
            Pageable pageable,
            Authentication authentication) {
        
        Long dealerId = ((UserPrincipal) authentication.getPrincipal()).getId();
        Page<Enquiry> enquiries = enquiryService.getDealerEnquiries(dealerId, pageable);
        
        return ResponseEntity.ok(ApiResponse.success("Dealer enquiries retrieved", enquiries));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('DEALER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Enquiry>> updateStatus(
            @PathVariable Long id,
            @RequestParam Enquiry.EnquiryStatus status) {
        
        Enquiry enquiry = enquiryService.updateEnquiryStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Status updated", enquiry));
    }
}
