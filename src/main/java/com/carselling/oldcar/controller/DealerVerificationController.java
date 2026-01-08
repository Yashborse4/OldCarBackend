package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.dealer.DealerVerificationRequestDto;
import com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.DealerVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for dealer verification operations
 * Endpoints for dealers to apply for and manage their verification requests
 */
@RestController
@RequestMapping("/api/dealer/verification")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('DEALER')")
public class DealerVerificationController {

    private final DealerVerificationService verificationService;

    /**
     * Apply for dealer verification
     * POST /api/dealer/verification/apply
     */
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<DealerVerificationResponseDto>> applyForVerification(
            @Valid @RequestBody DealerVerificationRequestDto request,
            Authentication authentication) {

        log.info("Dealer applying for verification");

        User dealer = (User) authentication.getPrincipal();
        DealerVerificationResponseDto response = verificationService.submitVerificationRequest(dealer.getId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Verification request submitted",
                "Your verification request has been submitted and is pending admin review.",
                response));
    }

    /**
     * Get current verification status
     * GET /api/dealer/verification/status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<DealerVerificationResponseDto>> getVerificationStatus(
            Authentication authentication) {

        log.info("Dealer checking verification status");

        User dealer = (User) authentication.getPrincipal();
        DealerVerificationResponseDto response = verificationService.getMyVerificationRequest(dealer.getId());

        if (response == null) {
            return ResponseEntity.ok(ApiResponse.success(
                    "No verification request",
                    "You have not submitted a verification request yet.",
                    null));
        }

        return ResponseEntity.ok(ApiResponse.success(
                "Verification status retrieved",
                "Your verification status: " + response.getStatusDisplayName(),
                response));
    }

    /**
     * Update/resubmit rejected verification request
     * PUT /api/dealer/verification/update
     */
    @PutMapping("/update")
    public ResponseEntity<ApiResponse<DealerVerificationResponseDto>> updateVerificationRequest(
            @Valid @RequestBody DealerVerificationRequestDto request,
            Authentication authentication) {

        log.info("Dealer updating verification request");

        User dealer = (User) authentication.getPrincipal();
        DealerVerificationResponseDto response = verificationService.updateVerificationRequest(dealer.getId(), request);

        return ResponseEntity.ok(ApiResponse.success(
                "Verification request updated",
                "Your verification request has been resubmitted for review.",
                response));
    }
}
