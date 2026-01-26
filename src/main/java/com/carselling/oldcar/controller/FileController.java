package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.model.MediaStatus;
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
import com.carselling.oldcar.service.CarService;
import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.repository.UserRepository;

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

    private final CarService carService;
    private final FileUploadService fileUploadService;
    private final com.carselling.oldcar.b2.B2FileService b2FileService;
    private final FileValidationService fileValidationService;
    private final com.carselling.oldcar.service.ChecksumService checksumService;
    private final UserRepository userRepository;

    /**
     * Upload single file
     */
    @PostMapping("/upload")
    @com.carselling.oldcar.annotation.RateLimit(capacity = 10, refill = 5, refillPeriod = 1)
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder,
            @RequestHeader(value = "X-File-Checksum", required = false) String checksum,
            Authentication authentication) {
        try {
            // Verify checksum if provided
            if (checksum != null && !checksumService.validateChecksum(file, checksum)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid Checksum", "message",
                                "The file checksum does not match provided MD5"));
            }
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            User currentUser = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId().toString()));

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
            ResourceType resourceType = ResourceType.OTHER;
            Long resourceOwnerId = currentUser.getId();

            if (folder.startsWith("users/")) {
                long pathUserId = extractIdFromPath(folder, "users/");
                if (pathUserId != -1) {
                    resourceType = ResourceType.USER_PROFILE;
                    resourceOwnerId = pathUserId;
                }
            } else if (folder.startsWith("cars/")) {
                long carId = extractIdFromPath(folder, "cars/");
                if (carId != -1) {
                    resourceType = ResourceType.CAR_IMAGE;
                    resourceOwnerId = carId;
                }
            }

            // Use B2FileService for storage
            FileUploadResponse response = b2FileService.uploadFile(file, folder, currentUser, resourceType,
                    resourceOwnerId);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (SecurityException e) {
            log.warn("Security validation failed for file upload by user {}: {}",
                    authentication.getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied", "message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid parameters for file upload by user {}: {}",
                    authentication.getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Invalid request parameters", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error uploading file by user {}: {}", authentication.getName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "File upload failed", "message", "An unexpected error occurred"));
        }
    }

    /**
     * Upload multiple files
     */
    @PostMapping("/upload/multiple")
    @com.carselling.oldcar.annotation.RateLimit(capacity = 10, refill = 5, refillPeriod = 1)
    public ResponseEntity<?> uploadMultipleFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "folder", defaultValue = "general") String folder,
            Authentication authentication) {
        try {
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            User currentUser = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId().toString()));

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
            ResourceType resourceType = ResourceType.OTHER;
            Long resourceOwnerId = currentUser.getId();

            if (folder.startsWith("users/")) {
                long pathUserId = extractIdFromPath(folder, "users/");
                if (pathUserId != -1) {
                    resourceType = ResourceType.USER_PROFILE;
                    resourceOwnerId = pathUserId;
                }
            } else if (folder.startsWith("cars/")) {
                long carId = extractIdFromPath(folder, "cars/");
                if (carId != -1) {
                    resourceType = ResourceType.CAR_IMAGE;
                    resourceOwnerId = carId;
                }
            }

            // Use B2FileService for storage
            List<FileUploadResponse> responses = b2FileService.uploadMultipleFiles(files, folder, currentUser,
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
    @com.carselling.oldcar.annotation.RateLimit(capacity = 20, refill = 10, refillPeriod = 1)
    public ResponseEntity<?> uploadCarImages(
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam("carId") Long carId,
            Authentication authentication) {
        try {
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            User currentUser = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId().toString()));

            if (carId == null || carId <= 0) {
                throw new IllegalArgumentException("Invalid car ID");
            }

            // STRICT OWNERSHIP CHECK
            com.carselling.oldcar.dto.car.CarResponse car = carService.getVehicleById(carId.toString());
            boolean isOwner = car.getDealerId().equals(currentUser.getId().toString());

            if (!isOwner && !isAdmin(currentUser)) {
                log.warn("Unauthorized attempt to upload images to car {} by user {}", carId,
                        currentUser.getUsername());
                throw new SecurityException("You do not have permission to modify this vehicle");
            }

            String folder = "cars/" + carId + "/images";
            fileValidationService.validateFiles(images);

            // Use B2FileService for storage
            List<FileUploadResponse> responses = b2FileService.uploadMultipleFiles(
                    images, folder, currentUser, ResourceType.CAR_IMAGE, carId);

            // Update Media Status to READY
            carService.updateMediaStatus(carId.toString(), MediaStatus.READY, currentUser.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Car images uploaded successfully",
                    "carId", carId,
                    "uploadedImages", responses));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied", "message", e.getMessage()));
        } catch (Exception e) {
            // Update Media Status to FAILED if possible
            try {
                if (carId != null && carId > 0) {
                    if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
                        Long userId = ((UserPrincipal) authentication.getPrincipal()).getId();
                        carService.updateMediaStatus(carId.toString(), MediaStatus.FAILED, userId);
                    }
                }
            } catch (Exception updateEx) {
                log.error("Failed to update media status to FAILED for car {}", carId, updateEx);
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload failed", "message", e.getMessage()));
        }
    }

    /**
     * DIRECT UPLOAD: Init
     */
    @PostMapping("/direct/init")
    @com.carselling.oldcar.annotation.RateLimit(capacity = 20, refill = 10, refillPeriod = 1)
    public ResponseEntity<?> initDirectUpload(
            @RequestBody com.carselling.oldcar.dto.file.DirectUploadDTOs.InitRequest request,
            Authentication authentication) {
        try {
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            User currentUser = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId().toString()));

            // File size validation (200MB limit)
            final long MAX_FILE_SIZE = 200L * 1024 * 1024;
            if (request.getContentLength() != null && request.getContentLength() > MAX_FILE_SIZE) {
                log.warn("File {} exceeds size limit", request.getFileName());
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                        "error", "FILE_TOO_LARGE", "message", "File size exceeds 200MB limit"));
            }

            // Authorization Check
            String folder = request.getFolder();

            // Auto-redirect videos to correct folder
            if (request.getContentType() != null && request.getContentType().startsWith("video/")
                    && folder.startsWith("cars/")) {
                folder = folder.replaceAll("/images/?$", "/videos");
            }
            validateFolderName(folder);
            checkFolderAuthorization(folder, currentUser);

            // Use B2FileService for init
            com.carselling.oldcar.b2.B2FileService.DirectUploadInitResponse response = b2FileService
                    .initDirectUpload(request.getFileName(), folder, currentUser);

            return ResponseEntity.ok(com.carselling.oldcar.dto.file.DirectUploadDTOs.InitResponse.builder()
                    .uploadUrl(response.getUploadUrl())
                    .authorizationToken(response.getAuthorizationToken())
                    .fileName(response.getFileName())
                    .fileUrl(response.getFileUrl())
                    .build());

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Init direct upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DIRECT UPLOAD: Complete
     */
    @PostMapping("/direct/complete")
    public ResponseEntity<?> completeDirectUpload(
            @RequestBody com.carselling.oldcar.dto.file.DirectUploadDTOs.CompleteRequest request,
            Authentication authentication) {
        try {
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            User currentUser = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId().toString()));

            // Context checks
            if (request.getFolder() != null) {
                checkFolderAuthorization(request.getFolder(), currentUser);
            }

            // Determine Owner
            ResourceType resourceType = ResourceType.OTHER;
            Long resourceOwnerId = currentUser.getId();

            if (request.getFolder() != null) {
                if (request.getFolder().startsWith("cars/")) {
                    resourceType = ResourceType.CAR_IMAGE;
                    long carId = extractIdFromPath(request.getFolder(), "cars/");
                    if (carId != -1)
                        resourceOwnerId = carId;
                } else if (request.getFolder().startsWith("users/")) {
                    resourceType = ResourceType.USER_PROFILE;
                    long userId = extractIdFromPath(request.getFolder(), "users/");
                    if (userId != -1)
                        resourceOwnerId = userId;
                }
            }
            if (request.getCarId() != null) {
                resourceType = ResourceType.CAR_IMAGE;
                resourceOwnerId = request.getCarId();
            }

            Object result = b2FileService.completeDirectUpload(
                    request.getB2FileName(),
                    request.getFileId(),
                    currentUser,
                    resourceType,
                    resourceOwnerId,
                    request.getFileSize(),
                    request.getOriginalFileName(),
                    request.getContentType());

            String responseFileUrl;
            String responseFileName;
            Long responseId;

            if (result instanceof com.carselling.oldcar.model.UploadedFile) {
                com.carselling.oldcar.model.UploadedFile uf = (com.carselling.oldcar.model.UploadedFile) result;
                responseFileUrl = uf.getFileUrl();
                responseFileName = uf.getFileName();
                responseId = uf.getId();
            } else if (result instanceof com.carselling.oldcar.model.TemporaryFile) {
                com.carselling.oldcar.model.TemporaryFile tf = (com.carselling.oldcar.model.TemporaryFile) result;
                responseFileUrl = tf.getFileUrl();
                responseFileName = tf.getFileName();
                responseId = tf.getId();
            } else {
                throw new RuntimeException("Unknown upload result type");
            }

            // If car image, update media status?
            // Ideally we should process media or set status.
            if (resourceType == ResourceType.CAR_IMAGE) {
                carService.updateMediaStatus(resourceOwnerId.toString(), MediaStatus.UPLOADED, currentUser.getId());
            }

            return ResponseEntity.ok(com.carselling.oldcar.dto.file.DirectUploadDTOs.CompleteResponse.builder()
                    .fileUrl(responseFileUrl)
                    .fileName(responseFileName)
                    .id(responseId)
                    .success(true)
                    .build());

        } catch (Exception e) {
            log.error("Complete direct upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    private void checkFolderAuthorization(String folder, User currentUser) {
        // STRICT AUTHORIZATION
        if (folder.startsWith("users/")) {
            long pathUserId = extractIdFromPath(folder, "users/");
            if (pathUserId != -1 && !currentUser.getId().equals(pathUserId) && !isAdmin(currentUser)) {
                throw new SecurityException("You are not authorized to upload to this folder");
            }
        } else if (folder.startsWith("cars/")) {
            long carId = extractIdFromPath(folder, "cars/");
            if (carId != -1) {
                com.carselling.oldcar.dto.car.CarResponse car = carService.getVehicleById(String.valueOf(carId));
                boolean isOwner = car.getDealerId().equals(currentUser.getId().toString());
                if (!isOwner && !isAdmin(currentUser)) {
                    throw new SecurityException("You are not authorized to upload to this car's folder");
                }
            }
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
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            User currentUser = userRepository.findById(principal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", principal.getId().toString()));

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
                            com.carselling.oldcar.dto.car.CarResponse car = carService
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
