package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.FileUploadService;
import com.carselling.oldcar.service.FileValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.service.CarService;

import java.util.List;
import java.util.Map;

/**
 * Controller for file upload and management operations
 */
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final com.carselling.oldcar.service.CarService carService;
    private final com.carselling.oldcar.repository.UserRepository userRepository;
    private final com.carselling.oldcar.service.FileUploadService fileUploadService;
    private final com.carselling.oldcar.service.FileValidationService fileValidationService;

    // ... upload single/multiple kept as is (or add validation if needed) ...

    /**
     * Upload single file
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();

            // Validate folder parameter to prevent path traversal
            validateFolderName(folder);

            // STRICT AUTHORIZATION: Prevent users from uploading to others' folders
            if (folder.startsWith("users/")) {
                long pathUserId = extractIdFromPath(folder, "users/");
                if (pathUserId != -1 && !currentUser.getId().equals(pathUserId) && !isAdmin(currentUser)) {
                    throw new SecurityException("You are not authorized to upload to this folder");
                }
            } else if (folder.startsWith("cars/")) {
                long carId = extractIdFromPath(folder, "cars/");
                if (carId != -1) {
                    boolean isOwner = carService.getVehicleById(String.valueOf(carId)).getDealerId()
                            .equals(currentUser.getId().toString());
                    if (!isOwner && !isAdmin(currentUser)) {
                        throw new SecurityException("You are not authorized to upload to this car's folder");
                    }
                }
            }

            // Validate file
            fileValidationService.validateFile(file);

            // Determine Resource Type and Owner ID
            com.carselling.oldcar.model.ResourceType resourceType = com.carselling.oldcar.model.ResourceType.OTHER;
            Long resourceOwnerId = currentUser.getId();

            if (folder.startsWith("users/")) {
                long pathUserId = extractIdFromPath(folder, "users/");
                if (pathUserId != -1) {
                    resourceType = com.carselling.oldcar.model.ResourceType.USER_PROFILE;
                    resourceOwnerId = pathUserId;
                }
            } else if (folder.startsWith("cars/")) {
                long carId = extractIdFromPath(folder, "cars/");
                if (carId != -1) {
                    resourceType = com.carselling.oldcar.model.ResourceType.CAR_IMAGE;
                    resourceOwnerId = carId;
                }
            }
            // If chat, need detection logic, but folder structure for chat not defined yet
            // in this controller snippet.

            FileUploadResponse response = fileUploadService.uploadFile(file, folder, currentUser, resourceType,
                    resourceOwnerId);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (SecurityException e) {
            log.warn("Security validation failed for file upload by user {}: {}",
                    authentication.getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            // ... existing catch ...
            log.warn("Invalid parameters for file upload by user {}: {}",
                    authentication.getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid request parameters", "message", e.getMessage()));
        } catch (Exception e) {
            // ... existing catch ...
            log.error("Error uploading file by user {}: {}", authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "File upload failed", "message", "An unexpected error occurred"));
        }
    }

    /**
     * Upload multiple files
     */
    @PostMapping("/upload/multiple")
    public ResponseEntity<?> uploadMultipleFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "folder", defaultValue = "general") String folder,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();

            validateFolderName(folder);

            // STRICT AUTHORIZATION
            if (folder.startsWith("users/")) {
                long pathUserId = extractIdFromPath(folder, "users/");
                if (pathUserId != -1 && !currentUser.getId().equals(pathUserId) && !isAdmin(currentUser)) {
                    throw new SecurityException("You are not authorized to upload to this folder");
                }
            } else if (folder.startsWith("cars/")) {
                long carId = extractIdFromPath(folder, "cars/");
                if (carId != -1) {
                    boolean isOwner = carService.getVehicleById(String.valueOf(carId)).getDealerId()
                            .equals(currentUser.getId().toString());
                    if (!isOwner && !isAdmin(currentUser)) {
                        throw new SecurityException("You are not authorized to upload to this car's folder");
                    }
                }
            }

            fileValidationService.validateFiles(files);

            // Determine Resource Type and Owner ID
            com.carselling.oldcar.model.ResourceType resourceType = com.carselling.oldcar.model.ResourceType.OTHER;
            Long resourceOwnerId = currentUser.getId();

            if (folder.startsWith("users/")) {
                long pathUserId = extractIdFromPath(folder, "users/");
                if (pathUserId != -1) {
                    resourceType = com.carselling.oldcar.model.ResourceType.USER_PROFILE;
                    resourceOwnerId = pathUserId;
                }
            } else if (folder.startsWith("cars/")) {
                long carId = extractIdFromPath(folder, "cars/");
                if (carId != -1) {
                    resourceType = com.carselling.oldcar.model.ResourceType.CAR_IMAGE;
                    resourceOwnerId = carId;
                }
            }

            List<FileUploadResponse> responses = fileUploadService.uploadMultipleFiles(files, folder, currentUser,
                    resourceType, resourceOwnerId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Files uploaded successfully",
                    "uploadedFiles", responses,
                    "totalFiles", files.size(),
                    "successfulUploads", responses.size()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Multiple file upload failed", "message", e.getMessage()));
        }
    }

    /**
     * Upload car images
     */
    @PostMapping("/upload/car-images")
    public ResponseEntity<?> uploadCarImages(
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam("carId") Long carId,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();

            if (carId == null || carId <= 0) {
                throw new IllegalArgumentException("Invalid car ID");
            }

            // STRICT OWNERSHIP CHECK
            com.carselling.oldcar.dto.car.CarResponseV2 car = carService.getVehicleById(carId.toString());
            boolean isOwner = car.getDealerId().equals(currentUser.getId().toString());

            if (!isOwner && !isAdmin(currentUser)) {
                log.warn("Unauthorized attempt to upload images to car {} by user {}", carId,
                        currentUser.getUsername());
                throw new SecurityException("You do not have permission to modify this vehicle");
            }

            String folder = "cars/" + carId + "/images";
            fileValidationService.validateFiles(images);

            List<FileUploadResponse> responses = fileUploadService.uploadMultipleFiles(
                    images, folder, currentUser, com.carselling.oldcar.model.ResourceType.CAR_IMAGE, carId);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Car images uploaded successfully",
                    "carId", carId,
                    "uploadedImages", responses));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed", "message", e.getMessage()));
        }
    }

    // ... uploadProfilePicture kept as is (uses currentUser.getId()) ...

    // ... uploadChatAttachment kept as is (needs real ChatService check ideally but
    // out of scope for Car/File request, but let's leave placeholder) ...

    /**
     * Delete file
     */
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteFile(
            @RequestParam("fileUrl") String fileUrl,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();

            if (fileUrl == null || fileUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("File URL cannot be empty");
            }

            // STRICT AUTHORIZATION for deletion
            if (!isAdmin(currentUser)) {
                if (fileUrl.contains("/users/")) {
                    long pathUserId = extractIdFromUrl(fileUrl, "/users/");
                    if (pathUserId != -1 && !currentUser.getId().equals(pathUserId)) {
                        throw new SecurityException("You can only delete your own profile files");
                    }
                } else if (fileUrl.contains("/cars/")) {
                    long carId = extractIdFromUrl(fileUrl, "/cars/");
                    if (carId != -1) {
                        // Check car ownership
                        try {
                            com.carselling.oldcar.dto.car.CarResponseV2 car = carService
                                    .getVehicleById(String.valueOf(carId));
                            if (!car.getDealerId().equals(currentUser.getId().toString())) {
                                throw new SecurityException("You cannot delete images from cars you do not own");
                            }
                        } catch (ResourceNotFoundException e) {
                            // If car doesn't exist, we might allow deletion if we assume orphaned file,
                            // OR block it. Safest is to block or allow admin only.
                            // But if file exists in storage but car is gone, user can't delete it?
                            // Let's assume strict: if car ID is in path, you must be owner of that car ID
                            // context.
                            throw new SecurityException("Associated vehicle not found or access denied");
                        }
                    }
                }
                // If generic folder or unknown structure, consider restricting deletions or
                // allow only uploader
                // (but we don't have uploader DB for generic files yet).
                // For now, assuming most files are structured.
            }

            boolean deleted = fileUploadService.deleteFile(fileUrl);

            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "File not found"));
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied", "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Deletion failed", "message", e.getMessage()));
        }
    }

    // Helper methods for validation

    private boolean isAdmin(User user) {
        return user.getRole().name().equals("ADMIN");
    }

    private long extractIdFromPath(String path, String prefix) {
        try {
            int startIndex = path.indexOf(prefix) + prefix.length();
            int endIndex = path.indexOf("/", startIndex);
            if (endIndex == -1)
                endIndex = path.length();
            return Long.parseLong(path.substring(startIndex, endIndex));
        } catch (Exception e) {
            return -1;
        }
    }

    private long extractIdFromUrl(String url, String pattern) {
        try {
            int startIndex = url.indexOf(pattern) + pattern.length();
            int endIndex = url.indexOf("/", startIndex);
            if (endIndex == -1)
                return -1; // Should allow reading until end if ID is last param? unlikely for URL
            return Long.parseLong(url.substring(startIndex, endIndex));
        } catch (Exception e) {
            return -1;
        }
    }

    // ... existing metadata/health methods ...

    /**
     * Generate presigned URL for secure access
     */
    @GetMapping("/presigned-url")
    public ResponseEntity<?> generatePresignedUrl(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(value = "expirationMinutes", defaultValue = "60") int expirationMinutes,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();

            // Validate parameters
            if (fileUrl == null || fileUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("File URL cannot be empty");
            }

            if (expirationMinutes <= 0 || expirationMinutes > 1440) { // Max 24 hours
                throw new IllegalArgumentException("Expiration minutes must be between 1 and 1440");
            }

            // Check for suspicious patterns in URL
            if (fileUrl.contains("..") || fileUrl.contains("~") || fileUrl.contains("%2e%2e")) {
                throw new SecurityException("Invalid file URL");
            }

            String presignedUrl = fileUploadService.generatePresignedUrl(fileUrl, expirationMinutes);

            log.info("Presigned URL generated by user {} for file: {}", authentication.getName(), fileUrl);

            return ResponseEntity.ok(Map.of(
                    "originalUrl", fileUrl,
                    "presignedUrl", presignedUrl,
                    "expirationMinutes", expirationMinutes));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters for presigned URL generation by user {}: {}",
                    authentication.getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid request parameters", "message", e.getMessage()));
        } catch (SecurityException e) {
            log.warn("Security validation failed for presigned URL generation by user {}: {}",
                    authentication.getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "File validation failed", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error generating presigned URL by user {}: {}", authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate presigned URL", "message",
                            "An unexpected error occurred"));
        }
    }

    /**
     * Get file metadata
     */
    @GetMapping("/metadata")
    public ResponseEntity<?> getFileMetadata(
            @RequestParam("fileUrl") String fileUrl,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();

            // Validate file URL
            if (fileUrl == null || fileUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("File URL cannot be empty");
            }

            // Check for suspicious patterns in URL
            if (fileUrl.contains("..") || fileUrl.contains("~") || fileUrl.contains("%2e%2e")) {
                throw new SecurityException("Invalid file URL");
            }

            var metadata = fileUploadService.getFileMetadata(fileUrl);

            if (metadata != null) {
                log.info("File metadata retrieved by user {} for file: {}", authentication.getName(), fileUrl);
                return ResponseEntity.ok(Map.of(
                        "fileUrl", fileUrl,
                        "contentType", metadata.getContentType(),
                        "contentLength", metadata.getContentLength(),
                        "lastModified", metadata.getLastModified(),
                        "userMetadata", metadata.getUserMetadata()));
            } else {
                log.warn("File metadata not found for user {}: {}", authentication.getName(), fileUrl);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "File metadata not found"));
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters for file metadata retrieval by user {}: {}",
                    authentication.getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid request parameters", "message", e.getMessage()));
        } catch (SecurityException e) {
            log.warn("Security validation failed for file metadata retrieval by user {}: {}",
                    authentication.getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "File validation failed", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting file metadata by user {}: {}", authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get file metadata", "message", "An unexpected error occurred"));
        }
    }

    /**
     * Health check endpoint for file service
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "service", "File Upload Service",
                "status", "UP",
                "timestamp", System.currentTimeMillis()));
    }

    /**
     * Validate folder name to prevent path traversal attacks
     */
    private void validateFolderName(String folder) {
        if (folder == null || folder.trim().isEmpty()) {
            throw new IllegalArgumentException("Folder name cannot be empty");
        }

        // Remove any path traversal characters
        String sanitized = folder.replaceAll("[\\./]+", "").trim();

        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Invalid folder name");
        }

        // Check for suspicious patterns
        if (folder.contains("..") || folder.contains("~") || folder.contains("%")) {
            throw new IllegalArgumentException("Folder name contains invalid characters");
        }

        // Additional validation: only allow alphanumeric, dash, underscore, and forward
        // slash
        if (!folder.matches("^[a-zA-Z0-9/_-]+$")) {
            throw new IllegalArgumentException(
                    "Folder name contains invalid characters. Only alphanumeric, dash, underscore, and forward slash are allowed");
        }

        // Limit length
        if (folder.length() > 100) {
            throw new IllegalArgumentException("Folder name is too long (max 100 characters)");
        }
    }
}
