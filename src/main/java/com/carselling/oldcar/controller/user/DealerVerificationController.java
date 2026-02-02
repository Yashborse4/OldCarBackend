package com.carselling.oldcar.controller.user;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.dealer.DealerVerificationRequestDto;
import com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto;
import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.user.DealerVerificationService;
import com.carselling.oldcar.annotation.RateLimit;
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
@io.swagger.v3.oas.annotations.tags.Tag(name = "Dealer Verification", description = "Dealer verification process management")
public class DealerVerificationController {

        private final DealerVerificationService verificationService;

        /**
         * Apply for dealer verification
         * POST /api/dealer/verification/apply
         */
        @PostMapping("/apply")
        @RateLimit(capacity = 3, refill = 1, refillPeriod = 5)
        @io.swagger.v3.oas.annotations.Operation(summary = "Apply for verification", description = "Submit a new dealer verification request")
        public ResponseEntity<ApiResponse<DealerVerificationResponseDto>> applyForVerification(
                        @Valid @RequestBody DealerVerificationRequestDto request,
                        Authentication authentication) {

                UserPrincipal dealer = (UserPrincipal) authentication.getPrincipal();
                log.info("Dealer {} applying for verification", dealer.getId());
                DealerVerificationResponseDto response = verificationService.submitVerificationRequest(dealer.getId(),
                                request);

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
        @RateLimit(capacity = 20, refill = 5, refillPeriod = 1)
        @io.swagger.v3.oas.annotations.Operation(summary = "Get verification status", description = "Retrieve the current status of your dealer verification")
        public ResponseEntity<ApiResponse<DealerVerificationResponseDto>> getVerificationStatus(
                        Authentication authentication) {

                UserPrincipal dealer = (UserPrincipal) authentication.getPrincipal();
                log.info("Dealer {} checking verification status", dealer.getId());
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
        @RateLimit(capacity = 3, refill = 1, refillPeriod = 5)
        public ResponseEntity<ApiResponse<DealerVerificationResponseDto>> updateVerificationRequest(
                        @Valid @RequestBody DealerVerificationRequestDto request,
                        Authentication authentication) {

                UserPrincipal dealer = (UserPrincipal) authentication.getPrincipal();
                log.info("Dealer {} updating verification request", dealer.getId());
                DealerVerificationResponseDto response = verificationService.updateVerificationRequest(dealer.getId(),
                                request);

                return ResponseEntity.ok(ApiResponse.success(
                                "Verification request updated",
                                "Your verification request has been resubmitted for review.",
                                response));
        }
}
