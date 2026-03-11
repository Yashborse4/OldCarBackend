package com.carselling.oldcar.controller.file;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.model.UploadedFile;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.util.SecurityUtils;
import com.carselling.oldcar.service.file.TempFileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Generic File Upload Controller.
 * Provides a simple multipart file upload endpoint that returns a file URL.
 * Used by dealer verification and other general-purpose file uploads.
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "File Upload", description = "General-purpose file upload operations")
public class FileUploadController {

        private final TempFileStorageService tempFileStorageService;
        private final UserRepository userRepository;

        /**
         * Upload a single file to temporary storage.
         * POST /api/files/upload?folder=general
         *
         * @param file   The multipart file to upload
         * @param folder Optional folder hint (e.g. "dealer_verification", "general")
         * @return ApiResponse containing the uploaded file metadata
         */
        @PostMapping("/upload")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Upload a file", description = "Upload a file to temporary storage and receive a URL")
        public ResponseEntity<ApiResponse<Map<String, Object>>> uploadFile(
                        @RequestParam("file") MultipartFile file,
                        @RequestParam(value = "folder", defaultValue = "general") String folder) {

                log.info("File upload request: {} (folder: {}, size: {} bytes)",
                                file.getOriginalFilename(), folder, file.getSize());

                try {
                        Long currentUserId = SecurityUtils.getCurrentUserId();
                        User currentUser = userRepository.findById(currentUserId)
                                        .orElseThrow(() -> new com.carselling.oldcar.exception.ResourceNotFoundException(
                                                        "User", "id", currentUserId.toString()));

                        // Determine resource type from folder hint
                        ResourceType resourceType = resolveResourceType(folder);

                        UploadedFile uploadedFile = tempFileStorageService.storeTempFile(
                                        file,
                                        currentUser,
                                        resourceType,
                                        currentUserId // Use user ID as owner for general uploads
                        );

                        Map<String, Object> responseData = Map.of(
                                        "id", uploadedFile.getId(),
                                        "fileUrl", uploadedFile.getFileUrl(),
                                        "fileName", uploadedFile.getFileName(),
                                        "originalFileName", uploadedFile.getOriginalFileName() != null
                                                        ? uploadedFile.getOriginalFileName()
                                                        : "",
                                        "contentType", uploadedFile.getContentType() != null
                                                        ? uploadedFile.getContentType()
                                                        : "",
                                        "size", uploadedFile.getSize() != null ? uploadedFile.getSize() : 0);

                        return ResponseEntity.ok(ApiResponse.success(
                                        "File uploaded successfully",
                                        "File has been stored in temporary storage",
                                        responseData));

                } catch (IllegalArgumentException e) {
                        log.error("File validation failed: {}", e.getMessage());
                        return ResponseEntity.badRequest().body(ApiResponse.error(
                                        "File validation failed",
                                        e.getMessage()));
                } catch (IOException e) {
                        log.error("Error storing file", e);
                        return ResponseEntity.internalServerError().body(ApiResponse.error(
                                        "Error storing file",
                                        "Failed to store file in temporary storage"));
                }
        }

        /**
         * Resolve the resource type from the folder hint string.
         */
        private ResourceType resolveResourceType(String folder) {
                if (folder == null)
                        return ResourceType.OTHER;
                String lower = folder.toLowerCase();
                if (lower.contains("dealer") || lower.contains("verification")) {
                        return ResourceType.DEALER_VERIFICATION;
                }
                if (lower.contains("car") || lower.contains("vehicle")) {
                        return ResourceType.CAR_IMAGE;
                }
                if (lower.contains("chat")) {
                        return ResourceType.CHAT_ATTACHMENT;
                }
                if (lower.contains("profile")) {
                        return ResourceType.USER_PROFILE;
                }
                return ResourceType.OTHER;
        }
}
