package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.media.CompleteUploadRequest;
import com.carselling.oldcar.dto.media.InitUploadRequest;
import com.carselling.oldcar.dto.media.InitUploadResponse;
import com.carselling.oldcar.model.MediaStatus;
import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.AsyncMediaService;
import com.carselling.oldcar.service.CarService;
import com.carselling.oldcar.service.FirebaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Slf4j
public class MediaController {

    private final FirebaseStorageService firebaseStorageService;
    private final AsyncMediaService asyncMediaService;
    private final CarService carService;

    // Allowed content types for media uploads
    private static final java.util.Set<String> ALLOWED_IMAGE_TYPES = java.util.Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp");
    private static final java.util.Set<String> ALLOWED_VIDEO_TYPES = java.util.Set.of(
            "video/mp4", "video/quicktime", "video/x-msvideo", "video/webm");

    @PostMapping("/init-upload")
    @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
    public ResponseEntity<ApiResponse<InitUploadResponse>> initUpload(
            @RequestBody InitUploadRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Initializing upload for car: {} by user: {}", request.getCarId(), currentUser.getId());

        // Update status to UPLOADING (This also verifies ownership)
        carService.updateMediaStatus(String.valueOf(request.getCarId()), MediaStatus.UPLOADING, currentUser.getId());

        String sessionId = UUID.randomUUID().toString();
        List<String> uploadUrls = new ArrayList<>();
        List<String> filePaths = new ArrayList<>();

        if (request.getFileNames() != null) {
            for (int i = 0; i < request.getFileNames().size(); i++) {
                String fileName = request.getFileNames().get(i);
                String contentType = (request.getContentTypes() != null && request.getContentTypes().size() > i)
                        ? request.getContentTypes().get(i)
                        : "application/octet-stream";

                // Validate content type before generating signed URL
                if (!isAllowedContentType(contentType)) {
                    log.warn("Rejected upload request for disallowed content type: {} (file: {})", contentType,
                            fileName);
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("Invalid content type: " + contentType
                                    + ". Only images (JPEG, PNG, GIF, WebP) and videos (MP4, MOV, AVI, WebM) are allowed."));
                }

                // Create a unique path: cars/{carId}/raw/{sessionId}/{fileName}
                String path = String.format("cars/%d/raw/%s/%s", request.getCarId(), sessionId, fileName);

                // Generate Signed URL (valid for 15 mins)
                String signedUrl = firebaseStorageService.generateSignedUrl(path, 15, "PUT", contentType);

                if (signedUrl != null) {
                    uploadUrls.add(signedUrl);
                    filePaths.add(path);
                } else {
                    log.error("Failed to generate signed URL for {}", fileName);
                }
            }
        }

        InitUploadResponse response = InitUploadResponse.builder()
                .sessionId(sessionId)
                .uploadUrls(uploadUrls)
                .filePaths(filePaths)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Upload initialized", "Ready to upload", response));
    }

    /**
     * Check if content type is allowed for upload
     */
    private boolean isAllowedContentType(String contentType) {
        if (contentType == null)
            return false;
        String normalizedType = contentType.toLowerCase().trim();
        return ALLOWED_IMAGE_TYPES.contains(normalizedType) || ALLOWED_VIDEO_TYPES.contains(normalizedType);
    }

    @PostMapping("/complete")
    @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> completeUpload(
            @RequestBody CompleteUploadRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Completing upload for car: {}, session: {}", request.getCarId(), request.getSessionId());

        if (request.isSuccess()) {
            // Update status to UPLOADED
            carService.updateMediaStatus(String.valueOf(request.getCarId()), MediaStatus.UPLOADED, currentUser.getId());

            // Trigger Async Processing
            asyncMediaService.processMedia(request.getCarId(), request.getUploadedFilePaths());
            return ResponseEntity
                    .ok(ApiResponse.success("Processing started", "Media is being processed in background", null));
        } else {
            // Update status to FAILED
            carService.updateMediaStatus(String.valueOf(request.getCarId()), MediaStatus.FAILED, currentUser.getId());

            log.warn("Upload reported failed by client for car {}", request.getCarId());
            return ResponseEntity.ok(ApiResponse.success("Failure recorded", "Upload failure recorded", null));
        }
    }
}
