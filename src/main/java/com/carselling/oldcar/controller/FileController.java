package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.file.DirectUploadDTOs;
import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.exception.ResourceNotFoundException;

import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.MediaService;
import com.carselling.oldcar.annotation.RateLimit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

/**
 * Controller for file upload and management operations.
 * Thin layer that delegates to MediaService for business logic.
 */
@RestController
@RequestMapping("/api/files")
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "File Management", description = "File upload and retrieval operations")
public class FileController {

    private final MediaService mediaService;

    public FileController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    // ===================== Upload Endpoints =====================

    /**
     * Upload single file
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @io.swagger.v3.oas.annotations.Operation(summary = "Upload file", description = "Upload a single file")
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder,
            @RequestHeader(value = "X-File-Checksum", required = false) String checksum,
            @AuthenticationPrincipal UserPrincipal principal) {

        FileUploadResponse response = mediaService.uploadSingleFile(file, folder, checksum, principal.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("File uploaded successfully", response));
    }

    /**
     * Upload multiple files
     */
    @PostMapping("/upload/multiple")
    @RateLimit(capacity = 10, refill = 5, refillPeriod = 1)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadMultipleFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "folder", defaultValue = "general") String folder,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> responseData = mediaService.uploadMultipleFiles(files, folder, principal.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Files uploaded successfully", responseData));
    }

    /**
     * Upload car images
     */
    @PostMapping("/upload/car-images")
    @RateLimit(capacity = 20, refill = 10, refillPeriod = 1)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadCarImages(
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam("carId") Long carId,
            @AuthenticationPrincipal UserPrincipal principal) {

        Map<String, Object> responseData = mediaService.uploadCarImages(images, carId, principal.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Car images uploaded, processing started", responseData));
    }

    /**
     * DIRECT UPLOAD: Init
     */
    @PostMapping("/direct/init")
    @RateLimit(capacity = 20, refill = 10, refillPeriod = 1)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<DirectUploadDTOs.InitResponse>> initDirectUpload(
            @RequestBody DirectUploadDTOs.InitRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        DirectUploadDTOs.InitResponse initResponse = mediaService.initDirectUpload(request, principal.getId());

        return ResponseEntity.ok(ApiResponse.success("Direct upload initialized", initResponse));
    }

    /**
     * DIRECT UPLOAD: Complete
     */
    @PostMapping("/direct-upload-url")
    @PreAuthorize("isAuthenticated()")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get direct upload URL", description = "Get a presigned URL for direct file upload")
    public ResponseEntity<ApiResponse<DirectUploadDTOs.CompleteResponse>> getDirectUploadUrl(
            @Valid @RequestBody DirectUploadDTOs.CompleteRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        DirectUploadDTOs.CompleteResponse completeResponse = mediaService.completeDirectUpload(request,
                principal.getId());

        return ResponseEntity.ok(ApiResponse.success("Direct upload completed", completeResponse));
    }

    /**
     * Delete file
     */
    @DeleteMapping("/{fileId}")
    @PreAuthorize("isAuthenticated()")
    @io.swagger.v3.oas.annotations.Operation(summary = "Delete file", description = "Delete a file by ID")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteFile(
            @PathVariable String fileId,
            @AuthenticationPrincipal UserPrincipal principal) {

        mediaService.deleteFile(fileId, principal.getId());

        return ResponseEntity.ok(
                ApiResponse.success("File deleted successfully", Map.of("message", "File deleted successfully")));
    }

    /**
     * Generate presigned URL for secure access
     */
    @GetMapping("/presigned-url")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generatePresignedUrl(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(value = "expirationMinutes", defaultValue = "60") int expirationMinutes,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String presignedUrl = mediaService.generatePresignedUrl(fileUrl, expirationMinutes, principal.getId());

        log.info("Presigned URL generated by user {} for file: {}", authentication.getName(), fileUrl);

        Map<String, Object> responseData = Map.of(
                "originalUrl", fileUrl,
                "presignedUrl", presignedUrl,
                "expirationMinutes", expirationMinutes);

        return ResponseEntity.ok(ApiResponse.success("Presigned URL generated", responseData));
    }

    /**
     * Get file metadata
     */
    @GetMapping("/metadata")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFileMetadata(
            @RequestParam("fileUrl") String fileUrl,
            Authentication authentication) {

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Map<String, Object> responseData = mediaService.getFileMetadata(fileUrl, principal.getId());

        if (responseData != null) {
            log.info("File metadata retrieved by user {} for file: {}", authentication.getName(), fileUrl);
            return ResponseEntity.ok(ApiResponse.success("File metadata retrieved", responseData));
        } else {
            throw new ResourceNotFoundException("File metadata", "url", fileUrl);
        }
    }

    /**
     * Health check endpoint for file service
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        Map<String, Object> healthData = Map.of(
                "service", "File Upload Service",
                "status", "UP",
                "timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(ApiResponse.success("Service is healthy", healthData));
    }
}
