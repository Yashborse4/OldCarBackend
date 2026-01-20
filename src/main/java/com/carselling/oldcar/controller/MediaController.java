package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.media.CompleteUploadRequest;
import com.carselling.oldcar.dto.media.InitUploadRequest;
import com.carselling.oldcar.dto.media.InitUploadResponse;
import com.carselling.oldcar.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Slf4j
public class MediaController {

    // DEPRECATED: This controller relied on Firebase Storage Signed URLs.
    // Please use FileController's /api/files/upload/car-images for B2 Native
    // Uploads.

    @PostMapping("/init-upload")
    @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
    public ResponseEntity<ApiResponse<InitUploadResponse>> initUpload(
            @RequestBody InitUploadRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.warn("Deprecated /init-upload called by user {}", currentUser.getId());
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.error("This upload method is deprecated. Please use /api/files/upload/car-images."));
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> completeUpload(
            @RequestBody CompleteUploadRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.warn("Deprecated /complete called by user {}", currentUser.getId());
        return ResponseEntity.status(HttpStatus.GONE)
                .body(ApiResponse.error("This upload method is deprecated."));
    }
}
