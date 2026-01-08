package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.dealer.DealerVerificationRequestDto;
import com.carselling.oldcar.dto.dealer.DealerVerificationResponseDto;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.exception.UnauthorizedActionException;
import com.carselling.oldcar.model.DealerStatus;
import com.carselling.oldcar.model.DealerVerificationRequest;
import com.carselling.oldcar.model.DealerVerificationRequest.VerificationStatus;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.DealerVerificationRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Service for dealer verification workflow
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DealerVerificationService {

    private final DealerVerificationRepository verificationRepository;
    private final UserRepository userRepository;

    /**
     * Submit a new verification request (POST /api/dealer/verification/apply)
     */
    public DealerVerificationResponseDto submitVerificationRequest(Long dealerId, DealerVerificationRequestDto dto) {
        log.info("Dealer {} submitting verification request", dealerId);

        User dealer = userRepository.findById(dealerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", dealerId.toString()));

        // Check if dealer role
        if (dealer.getRole() != Role.DEALER) {
            throw new UnauthorizedActionException("Only dealers can submit verification requests");
        }

        // Check if already verified
        if (dealer.isDealerVerified()) {
            throw new IllegalStateException("You are already a verified dealer");
        }

        // Check if already has pending request
        if (verificationRepository.existsByDealerIdAndStatus(dealerId, VerificationStatus.PENDING)) {
            throw new IllegalStateException("You already have a pending verification request");
        }

        // Check if already verified
        if (dealer.getDealerStatus() == DealerStatus.VERIFIED) {
            throw new IllegalStateException("You are already a verified dealer");
        }

        DealerVerificationRequest request = DealerVerificationRequest.builder()
                .dealer(dealer)
                .businessName(dto.getBusinessName())
                .businessAddress(dto.getBusinessAddress())
                .gstNumber(dto.getGstNumber())
                .phoneNumber(dto.getPhoneNumber())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .formattedAddress(dto.getFormattedAddress())
                .showroomExteriorImage(dto.getShowroomExteriorImage())
                .showroomInteriorImage(dto.getShowroomInteriorImage())
                .visitingCardImage(dto.getVisitingCardImage())
                .showroomImages(dto.getShowroomImages() != null ? dto.getShowroomImages() : new ArrayList<>())
                .infoConfirmed(dto.getInfoConfirmed())
                .termsAccepted(dto.getTermsAccepted())
                .status(VerificationStatus.PENDING)
                .build();

        DealerVerificationRequest saved = verificationRepository.save(request);

        // Update dealer status to PENDING (UNVERIFIED with pending request)
        dealer.updateDealerStatus(DealerStatus.UNVERIFIED, "Verification request submitted");
        userRepository.save(dealer);

        log.info("Verification request {} created for dealer {}", saved.getId(), dealerId);

        return convertToResponseDto(saved);
    }

    /**
     * Update an existing verification request (PUT /api/dealer/verification/update)
     * Only allowed if current request is REJECTED
     */
    public DealerVerificationResponseDto updateVerificationRequest(Long dealerId, DealerVerificationRequestDto dto) {
        log.info("Dealer {} updating verification request", dealerId);

        User dealer = userRepository.findById(dealerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", dealerId.toString()));

        if (dealer.isDealerVerified()) {
            throw new IllegalStateException("You are already a verified dealer");
        }

        DealerVerificationRequest existing = verificationRepository.findLatestByDealerId(dealerId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("VerificationRequest", "dealerId", dealerId.toString()));

        // Only allow update if rejected
        if (existing.getStatus() != VerificationStatus.REJECTED) {
            throw new IllegalStateException("You can only update a rejected verification request");
        }

        // Update fields
        existing.setBusinessName(dto.getBusinessName());
        existing.setBusinessAddress(dto.getBusinessAddress());
        existing.setGstNumber(dto.getGstNumber());
        existing.setPhoneNumber(dto.getPhoneNumber());
        existing.setLatitude(dto.getLatitude());
        existing.setLongitude(dto.getLongitude());
        existing.setFormattedAddress(dto.getFormattedAddress());
        existing.setShowroomExteriorImage(dto.getShowroomExteriorImage());
        existing.setShowroomInteriorImage(dto.getShowroomInteriorImage());
        existing.setVisitingCardImage(dto.getVisitingCardImage());
        existing.setShowroomImages(dto.getShowroomImages() != null ? dto.getShowroomImages() : new ArrayList<>());
        existing.setInfoConfirmed(dto.getInfoConfirmed());
        existing.setTermsAccepted(dto.getTermsAccepted());
        existing.setStatus(VerificationStatus.PENDING);
        existing.setAdminNotes(null);
        existing.setReviewedAt(null);
        existing.setReviewedBy(null);

        DealerVerificationRequest saved = verificationRepository.save(existing);
        log.info("Verification request {} updated and resubmitted", saved.getId());

        return convertToResponseDto(saved);
    }

    /**
     * Get dealer's latest verification request (GET
     * /api/dealer/verification/status)
     */
    @Transactional(readOnly = true)
    public DealerVerificationResponseDto getMyVerificationRequest(Long dealerId) {
        return verificationRepository.findLatestByDealerId(dealerId)
                .map(this::convertToResponseDto)
                .orElse(null);
    }

    /**
     * Get verification request by ID
     */
    @Transactional(readOnly = true)
    public DealerVerificationResponseDto getVerificationRequestById(Long requestId) {
        DealerVerificationRequest request = verificationRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("VerificationRequest", "id", requestId.toString()));
        return convertToResponseDto(request);
    }

    /**
     * Get all requests by status (for admin)
     */
    @Transactional(readOnly = true)
    public Page<DealerVerificationResponseDto> getRequestsByStatus(VerificationStatus status, Pageable pageable) {
        Page<DealerVerificationRequest> requests;
        if (status != null) {
            requests = verificationRepository.findByStatus(status, pageable);
        } else {
            requests = verificationRepository.findAll(pageable);
        }
        return requests.map(this::convertToResponseDto);
    }

    /**
     * Approve a verification request
     */
    public DealerVerificationResponseDto approveVerificationRequest(Long requestId, Long adminId) {
        log.info("Admin {} approving verification request {}", adminId, requestId);

        DealerVerificationRequest request = verificationRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("VerificationRequest", "id", requestId.toString()));

        if (request.getStatus() != VerificationStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be approved");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", adminId.toString()));

        // Update request
        request.setStatus(VerificationStatus.APPROVED);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewedBy(admin);

        // Update dealer status
        User dealer = request.getDealer();
        dealer.updateDealerStatus(DealerStatus.VERIFIED, "Verification approved");
        dealer.setShowroomName(request.getBusinessName());
        dealer.setAddress(request.getBusinessAddress());
        if (request.getPhoneNumber() != null) {
            dealer.setPhoneNumber(request.getPhoneNumber());
        }
        userRepository.save(dealer);

        DealerVerificationRequest saved = verificationRepository.save(request);
        log.info("Verification request {} approved, dealer {} is now verified", requestId, dealer.getId());

        return convertToResponseDto(saved);
    }

    /**
     * Reject a verification request
     */
    public DealerVerificationResponseDto rejectVerificationRequest(Long requestId, Long adminId, String reason) {
        log.info("Admin {} rejecting verification request {} with reason: {}", adminId, requestId, reason);

        DealerVerificationRequest request = verificationRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("VerificationRequest", "id", requestId.toString()));

        if (request.getStatus() != VerificationStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be rejected");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", adminId.toString()));

        // Update request
        request.setStatus(VerificationStatus.REJECTED);
        request.setAdminNotes(reason);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewedBy(admin);

        DealerVerificationRequest saved = verificationRepository.save(request);
        log.info("Verification request {} rejected", requestId);

        return convertToResponseDto(saved);
    }

    /**
     * Count pending requests (for admin dashboard)
     */
    @Transactional(readOnly = true)
    public long countPendingRequests() {
        return verificationRepository.countByStatus(VerificationStatus.PENDING);
    }

    // ============ Helper Methods ============

    private DealerVerificationResponseDto convertToResponseDto(DealerVerificationRequest request) {
        User dealer = request.getDealer();

        return DealerVerificationResponseDto.builder()
                .id(request.getId())
                .dealerId(dealer.getId())
                .dealerUsername(dealer.getUsername())
                .dealerEmail(dealer.getEmail())
                .dealerPhone(dealer.getPhoneNumber())
                .businessName(request.getBusinessName())
                .businessAddress(request.getBusinessAddress())
                .gstNumber(request.getGstNumber())
                .phoneNumber(request.getPhoneNumber())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .formattedAddress(request.getFormattedAddress())
                .showroomExteriorImage(request.getShowroomExteriorImage())
                .showroomInteriorImage(request.getShowroomInteriorImage())
                .visitingCardImage(request.getVisitingCardImage())
                .showroomImages(request.getShowroomImages())
                .status(request.getStatus().name())
                .statusDisplayName(request.getStatus().getDisplayName())
                .adminNotes(request.getAdminNotes())
                .submittedAt(request.getSubmittedAt())
                .reviewedAt(request.getReviewedAt())
                .reviewedByUsername(request.getReviewedBy() != null ? request.getReviewedBy().getUsername() : null)
                .build();
    }
}
