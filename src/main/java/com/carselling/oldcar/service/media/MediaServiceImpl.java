package com.carselling.oldcar.service;

import com.carselling.oldcar.b2.B2FileService;
import com.carselling.oldcar.dto.file.DirectUploadDTOs;
import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.exception.InvalidInputException;
import com.carselling.oldcar.exception.MediaUploadNotAllowedException;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.MediaStatus;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.model.TemporaryFile;
import com.carselling.oldcar.model.UploadedFile;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.car.CarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.carselling.oldcar.dto.file.FileMetadata;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaServiceImpl implements MediaService {

    private final B2FileService b2FileService;
    private final FileValidationService fileValidationService;
    private final FileAuthorizationService authService; // Renamed from fileAuthorizationService for brevity
    private final ChecksumService checksumService;
    private final CarService carService;
    private final AsyncMediaService asyncMediaService;
    private final FileUploadService fileUploadService; // Keeps legacy FileUploadService for some methods if needed, or
                                                       // we explicitly use B2
    private final com.carselling.oldcar.repository.UserRepository userRepository;
    private final com.carselling.oldcar.repository.UploadedFileRepository uploadedFileRepository;
    private final com.carselling.oldcar.repository.ChatParticipantRepository chatParticipantRepository;

    private User resolveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId.toString()));
    }

    @Override
    @Transactional
    public FileUploadResponse uploadSingleFile(MultipartFile file, String folder, String checksum, Long userId) {
        User user = resolveUser(userId);
        if (file == null || file.isEmpty()) {
            throw new InvalidInputException("File cannot be empty");
        }

        // 1. Validate File Basic
        fileValidationService.validateFile(file);

        // 2. Validate Checksum if provided
        if (checksum != null && !checksum.isBlank()) {
            try {
                if (!checksumService.validateChecksum(file, checksum)) {
                    throw new InvalidInputException("File checksum validation failed");
                }
            } catch (IOException e) {
                throw new MediaUploadNotAllowedException(
                        "Failed to read file for checksum validation: " + e.getMessage());
            }
        }

        // 3. Validate Folder & Auth
        fileValidationService.validateFolderName(folder);
        authService.checkFolderAuthorization(folder, user);

        // 4. Determine Context (Resource Type & Owner)
        ResourceType resourceType = ResourceType.OTHER;
        Long resourceOwnerId = user.getId();

        if (folder.startsWith("users/")) {
            long pathUserId = authService.extractIdFromPath(folder, "users/");
            if (pathUserId != -1) {
                resourceType = ResourceType.USER_PROFILE;
                resourceOwnerId = pathUserId;
            }
        } else if (folder.startsWith("cars/")) {
            long carId = authService.extractIdFromPath(folder, "cars/");
            if (carId != -1) {
                resourceType = ResourceType.CAR_IMAGE;
                resourceOwnerId = carId;
            }
        } else if (folder.startsWith("chat/")) {
            long chatId = authService.extractIdFromPath(folder, "chat/");
            if (chatId != -1) {
                resourceType = ResourceType.CHAT_ATTACHMENT;
                resourceOwnerId = chatId;
            }
        }

        // 5. Upload to B2
        try {
            return b2FileService.uploadFile(file, folder, user, resourceType, resourceOwnerId);
        } catch (IOException e) {
            log.error("Failed to upload file to B2: {}", e.getMessage(), e);
            throw new MediaUploadNotAllowedException("Failed to upload file to storage service: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Map<String, Object> uploadMultipleFiles(List<MultipartFile> files, String folder, Long userId) {
        User user = resolveUser(userId);
        // Validation & Authorization
        fileValidationService.validateFolderName(folder);
        authService.checkFolderAuthorization(folder, user);
        fileValidationService.validateFiles(files);

        // Determine Resource Type and Owner ID
        ResourceType resourceType = ResourceType.OTHER;
        Long resourceOwnerId = user.getId();

        if (folder.startsWith("users/")) {
            long pathUserId = authService.extractIdFromPath(folder, "users/");
            if (pathUserId != -1) {
                resourceType = ResourceType.USER_PROFILE;
                resourceOwnerId = pathUserId;
            }
        } else if (folder.startsWith("cars/")) {
            long carId = authService.extractIdFromPath(folder, "cars/");
            if (carId != -1) {
                resourceType = ResourceType.CAR_IMAGE;
                resourceOwnerId = carId;
            }
        } else if (folder.startsWith("chat/")) {
            long chatId = authService.extractIdFromPath(folder, "chat/");
            if (chatId != -1) {
                resourceType = ResourceType.CHAT_ATTACHMENT;
                resourceOwnerId = chatId;
            }
        }

        // Use B2FileService for storage
        List<FileUploadResponse> responses = b2FileService.uploadMultipleFiles(files, folder, user,
                resourceType, resourceOwnerId);

        return Map.of(
                "uploadedFiles", responses,
                "totalFiles", files.size(),
                "successfulUploads", responses.size());
    }

    @Override
    @Transactional
    public Map<String, Object> uploadCarImages(List<MultipartFile> images, Long carId, Long userId) {
        User user = resolveUser(userId);
        if (carId == null || carId <= 0) {
            throw new IllegalArgumentException("Invalid car ID");
        }

        // STRICT OWNERSHIP CHECK
        authService.checkFolderAuthorization("cars/" + carId + "/images", user);

        String folder = "cars/" + carId + "/images";
        fileValidationService.validateFiles(images);

        try {
            // Use B2FileService for storage
            List<FileUploadResponse> responses = b2FileService.uploadMultipleFiles(
                    images, folder, user, ResourceType.CAR_IMAGE, carId);

            // Update Media Status to PROCESSING
            carService.updateMediaStatus(carId.toString(), MediaStatus.PROCESSING, user.getId());

            // Trigger Async Processing
            List<String> fileUrls = responses.stream()
                    .map(FileUploadResponse::getFileUrl)
                    .toList();
            asyncMediaService.processMedia(carId, fileUrls);

            return Map.of(
                    "carId", carId,
                    "uploadedImages", responses,
                    "status", MediaStatus.PROCESSING);

        } catch (Exception e) {
            // Update Media Status to FAILED if possible
            try {
                carService.updateMediaStatus(carId.toString(), MediaStatus.FAILED, user.getId());
            } catch (Exception updateEx) {
                log.error("Failed to update media status to FAILED for car {}", carId, updateEx);
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public DirectUploadDTOs.InitResponse initDirectUpload(DirectUploadDTOs.InitRequest request, Long userId) {
        User user = resolveUser(userId);
        // File size validation (200MB limit)
        final long MAX_FILE_SIZE = 200L * 1024 * 1024;
        if (request.getContentLength() != null && request.getContentLength() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 200MB limit");
        }

        String folder = request.getFolder();
        // Note: B2FileService.initDirectUpload handles temp path routing
        // and preserves subfolders (images/videos) from the request.

        fileValidationService.validateFolderName(folder);
        authService.checkFolderAuthorization(folder, user);

        // Use B2FileService for init
        B2FileService.DirectUploadInitResponse response = b2FileService
                .initDirectUpload(request.getFileName(), folder, user);

        return DirectUploadDTOs.InitResponse.builder()
                .uploadUrl(response.getUploadUrl())
                .authorizationToken(response.getAuthorizationToken())
                .fileName(response.getFileName())
                .fileUrl(response.getFileUrl())
                .build();
    }

    @Override
    @Transactional
    public DirectUploadDTOs.CompleteResponse completeDirectUpload(DirectUploadDTOs.CompleteRequest request,
            Long userId) {
        User user = resolveUser(userId);
        // Context checks
        if (request.getFolder() != null) {
            authService.checkFolderAuthorization(request.getFolder(), user);
        }

        // Determine Owner
        ResourceType resourceType = ResourceType.OTHER;
        Long resourceOwnerId = user.getId();

        if (request.getFolder() != null) {
            if (request.getFolder().startsWith("cars/")) {
                resourceType = ResourceType.CAR_IMAGE;
                long carId = authService.extractIdFromPath(request.getFolder(), "cars/");
                if (carId != -1)
                    resourceOwnerId = carId;
            } else if (request.getFolder().startsWith("users/")) {
                resourceType = ResourceType.USER_PROFILE;
                long userIdVal = authService.extractIdFromPath(request.getFolder(), "users/");
                if (userIdVal != -1)
                    resourceOwnerId = userIdVal;
            } else if (request.getFolder().startsWith("chat/")) {
                resourceType = ResourceType.CHAT_ATTACHMENT;
                long chatId = authService.extractIdFromPath(request.getFolder(), "chat/");
                if (chatId != -1)
                    resourceOwnerId = chatId;
            }
        }
        if (request.getCarId() != null) {
            log.info("completeDirectUpload: Received carId in request: {}", request.getCarId());
            resourceType = ResourceType.CAR_IMAGE;
            resourceOwnerId = request.getCarId();
        } else {
            log.info("completeDirectUpload: No carId in request");
        }

        Object result = b2FileService.completeDirectUpload(
                request.getB2FileName(),
                request.getFileId(),
                user,
                resourceType,
                resourceOwnerId,
                request.getFileSize(),
                request.getOriginalFileName(),
                request.getContentType());

        // Response construction
        String responseFileUrl;
        String responseFileName;
        Long responseId;

        if (result instanceof UploadedFile) {
            UploadedFile uf = (UploadedFile) result;
            responseFileUrl = uf.getFileUrl();
            responseFileName = uf.getFileName();
            responseId = uf.getId();
        } else if (result instanceof TemporaryFile) {
            TemporaryFile tf = (TemporaryFile) result;
            responseFileUrl = tf.getFileUrl();
            responseFileName = tf.getFileName();
            responseId = tf.getId();
        } else {
            throw new RuntimeException("Unknown upload result type");
        }

        // NOTE: Do NOT trigger per-file async processing or status updates here.
        // With concurrent uploads (e.g. 4 images), each completeDirectUpload call would
        // spawn its own async thread, all racing to update the same Car entity and
        // causing
        // ObjectOptimisticLockingFailureException. Instead, the frontend calls
        // finalizeVehicleMedia after ALL files are uploaded, which is the single
        // correct
        // trigger point for bulk media processing.
        if (resourceType == ResourceType.CAR_IMAGE) {
            log.info("File registered for car {} — bulk processing deferred to finalizeVehicleMedia", resourceOwnerId);
        }

        return DirectUploadDTOs.CompleteResponse.builder()
                .fileUrl(responseFileUrl)
                .fileName(responseFileName)
                .id(responseId)
                .success(true)
                .build();
    }

    @Override
    public void deleteFile(String fileUrl, Long userId) {
        User user = resolveUser(userId);
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("File URL cannot be empty");
        }

        // STRICT AUTHORIZATION for deletion
        authService.checkDeletionAuthorization(fileUrl, user);

        boolean deleted = fileUploadService.deleteFile(fileUrl);

        if (!deleted) {
            // Try B2 direct delete if fileUploadService (legacy?) fails or just wrapper
            // If fileUploadService calls B2FileService, it's fine.
            // Assuming fileUploadService delegates correctly or we should use b2FileService
            // directly?
            // Checking FileController usage -> it used fileUploadService.deleteFile.
            throw new ResourceNotFoundException("File", "url", fileUrl);
        }
    }

    @Override
    public String generatePresignedUrl(String fileUrl, int expirationMinutes, Long userId) {
        User user = resolveUser(userId);
        // Validate parameters
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("File URL cannot be empty");
        }

        if (expirationMinutes <= 0 || expirationMinutes > 1440) { // Max 24 hours
            throw new IllegalArgumentException("Expiration minutes must be between 1 and 1440");
        }

        if (fileUrl.contains("..") || fileUrl.contains("~") || fileUrl.contains("%2e%2e")) {
            throw new SecurityException("Invalid file URL");
        }

        // STRICT AUTHORIZATION CHECK
        authService.checkDeletionAuthorization(fileUrl, user);

        // Using fileUploadService as it seems to handle this logic (maybe delegating to
        // B2)
        return fileUploadService.generatePresignedUrl(fileUrl, expirationMinutes);
    }

    @Override
    public Map<String, Object> getFileMetadata(String fileUrl, Long userId) {
        User user = resolveUser(userId);
        // Validate file URL
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("File URL cannot be empty");
        }

        // Check for suspicious patterns in URL
        if (fileUrl.contains("..") || fileUrl.contains("~") || fileUrl.contains("%2e%2e")) {
            throw new SecurityException("Invalid file URL");
        }

        // STRICT AUTHORIZATION CHECK
        authService.checkDeletionAuthorization(fileUrl, user);

        FileMetadata metadata = fileUploadService.getFileMetadata(fileUrl);

        if (metadata != null) {
            return Map.of(
                    "fileUrl", fileUrl,
                    "contentType",
                    metadata.getContentType() != null ? metadata.getContentType() : "application/octet-stream",
                    "contentLength", metadata.getContentLength() != null ? metadata.getContentLength() : 0L,
                    "lastModified", metadata.getLastModified() != null ? metadata.getLastModified() : 0L,
                    "userMetadata", metadata.getUserMetadata() != null ? metadata.getUserMetadata() : Map.of());
        }
        return null;
    }

    @Override
    public String getMediaFileUrl(Long id, Long userId) {
        User user = resolveUser(userId);
        UploadedFile file = uploadedFileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Media", "id", id.toString()));

        // Public Access Check - return direct URL
        if (file.getAccessType() == com.carselling.oldcar.model.AccessType.PUBLIC) {
            return file.getFileUrl();
        }

        // Private/Internal Access - requires granulated permission check
        if (!hasMediaAccess(file, user)) {
            throw new com.carselling.oldcar.exception.MediaUploadNotAllowedException("Access denied to private media");
        }

        // Generate Presigned URL for Private File (1 hour expiration)
        return fileUploadService.generatePresignedUrl(file.getFileUrl(), 60);
    }

    /**
     * Check if user has access to a private media file based on granulated
     * permissions.
     * Permission matrix:
     * - Owner: Always has access
     * - Admin: Always has access
     * - Car Owner: Access to their car's private images (CAR_IMAGE type)
     * - Chat Participant: Access to chat attachments (CHAT_ATTACHMENT - future)
     * - Others: No access to private files
     *
     * @param file The uploaded file to check access for
     * @param user The user requesting access
     * @return true if access is granted, false otherwise
     */
    private boolean hasMediaAccess(UploadedFile file, User user) {
        // 1. Owner always has access
        if (file.getUploadedBy() != null && file.getUploadedBy().getId().equals(user.getId())) {
            return true;
        }

        // 2. Admin has access to everything
        if (user.getRole() == com.carselling.oldcar.model.Role.ADMIN) {
            return true;
        }

        // 3. Role + ResourceType specific checks
        ResourceType resourceType = file.getOwnerType();
        if (resourceType == null) {
            return false; // Unknown resource type - deny access
        }

        switch (resourceType) {
            case CAR_IMAGE:
                // Users/Dealers can access private images of cars they own
                if (file.getOwnerId() != null) {
                    try {
                        var car = carService.getVehicleById(file.getOwnerId().toString());
                        return car.getDealerId() != null && car.getDealerId().equals(user.getId().toString());
                    } catch (ResourceNotFoundException e) {
                        log.warn("Car not found for media access check, carId: {}", file.getOwnerId());
                        return false;
                    }
                }
                return false;

            case CHAT_ATTACHMENT:
                if (file.getOwnerId() != null) {
                    return chatParticipantRepository
                            .findByChatRoomIdAndUserIdAndIsActiveTrue(file.getOwnerId(), user.getId())
                            .isPresent();
                }
                return false;

            case USER_PROFILE:
            case OTHER:
            default:
                // Only owner and admin can access private profile/other files
                // (owner and admin already checked above)
                return false;
        }
    }
}
