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
        // Auto-redirect videos to correct folder
        if (request.getContentType() != null && request.getContentType().startsWith("video/")
                && folder.startsWith("cars/")) {
            folder = folder.replaceAll("/images/?$", "/videos");
        }

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
            }
        }
        if (request.getCarId() != null) {
            resourceType = ResourceType.CAR_IMAGE;
            resourceOwnerId = request.getCarId();
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

        if (resourceType == ResourceType.CAR_IMAGE) {
            carService.updateMediaStatus(resourceOwnerId.toString(), MediaStatus.PROCESSING, user.getId());
            // Trigger async processing for this single file
            asyncMediaService.processMedia(resourceOwnerId, List.of(responseFileUrl));
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
}
