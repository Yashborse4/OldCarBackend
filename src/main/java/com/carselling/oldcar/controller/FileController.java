package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.dto.file.DirectUploadDTOs;
import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.exception.ResourceNotFoundException;

import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.media.MediaService;
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
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "File uploaded successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file or parameters"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "File too large")
        })
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Upload multiple files", description = "Upload multiple files at once")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Files uploaded successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid files or parameters"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Upload car images", description = "Upload images for a car listing")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Car images uploaded"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid images or car ID"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Car not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
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
        @PostMapping("/direct/complete")
        @PreAuthorize("isAuthenticated()")
        @io.swagger.v3.oas.annotations.Operation(summary = "Get direct upload URL", description = "Get a presigned URL for direct file upload")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Direct upload URL generated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
        })
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
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "File deleted successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not authorized to delete this file"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "File not found")
        })
        public ResponseEntity<ApiResponse<Map<String, String>>> deleteFile(
                        @PathVariable String fileId,
                        @AuthenticationPrincipal UserPrincipal principal) {

                mediaService.deleteFile(fileId, principal.getId());

                return ResponseEntity.ok(
                                ApiResponse.success("File deleted successfully",
                                                Map.of("message", "File deleted successfully")));
        }

        /**
         * Delete file by URL (query param version for easier URL encoding)
         */
        @DeleteMapping("/delete")
        @PreAuthorize("isAuthenticated()")
        @io.swagger.v3.oas.annotations.Operation(summary = "Delete file by URL", description = "Delete a file by its URL")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "File deleted successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not authorized to delete this file"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "File not found")
        })
        public ResponseEntity<ApiResponse<Map<String, String>>> deleteFileByUrl(
                        @RequestParam("fileUrl") String fileUrl,
                        @AuthenticationPrincipal UserPrincipal principal) {

                mediaService.deleteFile(fileUrl, principal.getId());

                return ResponseEntity.ok(
                                ApiResponse.success("File deleted successfully",
                                                Map.of("message", "File deleted successfully")));
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
