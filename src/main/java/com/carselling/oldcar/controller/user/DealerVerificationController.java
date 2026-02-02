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
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Verification request submitted successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data or validation error", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ApiResponse.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User is not a dealer or unauthorized", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ApiResponse.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Verification request already exists", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ApiResponse.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = ApiResponse.class)))
        })
        public ResponseEntity<ApiResponse<DealerVerificationResponseDto>> applyForVerification(
                        @Valid @RequestBody DealerVerificationRequestDto request,
                        Authentication authentication) {

                UserPrincipal dealer = (UserPrincipal) authentication.getPrincipal();
                log.info("Dealer {} submitting verification request via /api/user/dealer-verification", dealer.getId());
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
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Verification status retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User is not a dealer"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Update verification request", description = "Resubmit a rejected verification request with updated information")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Verification request updated successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "User is not a dealer or request is not in rejected state"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No verification request found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
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
