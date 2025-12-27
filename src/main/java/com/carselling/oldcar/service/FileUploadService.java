package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.file.FileMetadata;
import com.carselling.oldcar.dto.file.FileUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service for handling file uploads to Firebase Storage
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    private final FirebaseStorageService firebaseStorageService;
    private final com.carselling.oldcar.repository.UploadedFileRepository uploadedFileRepository;
    private final com.carselling.oldcar.repository.UserRepository userRepository;

    // Allowed file types and their MIME types
    private static final Map<String, Set<String>> ALLOWED_FILE_TYPES = Map.of(
            "image", Set.of("image/jpeg", "image/png", "image/gif", "image/webp"),
            "document", Set.of("application/pdf", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain"),
            "video", Set.of("video/mp4", "video/avi", "video/mov", "video/wmv"));

    // Maximum file sizes (in bytes)
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_DOCUMENT_SIZE = 20 * 1024 * 1024; // 20MB
    private static final long MAX_VIDEO_SIZE = 100 * 1024 * 1024; // 100MB

    /**
     * Upload file with strict ownership tracking
     */
    public FileUploadResponse uploadFile(MultipartFile file, String folder, com.carselling.oldcar.model.User uploader,
            com.carselling.oldcar.model.ResourceType ownerType, Long ownerId) throws IOException {
        // Validate file
        validateFile(file);

        String fileName = generateUniqueFileName(file.getOriginalFilename(), uploader.getId());
        String fullPath = folder + "/" + fileName;

        try {
            // Upload file to Firebase
            String fileUrl = firebaseStorageService.uploadFile(file, fullPath);

            log.info("File uploaded successfully: {} -> {}", file.getOriginalFilename(), fileUrl);

            // Save metadata to DB
            com.carselling.oldcar.model.UploadedFile uploadedFile = com.carselling.oldcar.model.UploadedFile.builder()
                    .fileUrl(fileUrl)
                    .fileName(fileName)
                    .originalFileName(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .size(file.getSize())
                    .uploadedBy(uploader)
                    .ownerType(ownerType)
                    .ownerId(ownerId)
                    .build();

            uploadedFileRepository.save(uploadedFile);

            return FileUploadResponse.builder()
                    .fileName(fileName)
                    .originalFileName(file.getOriginalFilename())
                    .fileUrl(fileUrl)
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .folder(folder)
                    .uploadedAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Error uploading file to Firebase: {}", e.getMessage(), e);
            throw new IOException("Failed to upload file: " + e.getMessage());
        }
    }

    /**
     * Legacy upload method (Deprecated) - Use strict version instead
     */
    public FileUploadResponse uploadFile(MultipartFile file, String folder, Long userId) throws IOException {
        com.carselling.oldcar.model.User user = userRepository.findById(userId)
                .orElseThrow(() -> new IOException("User not found: " + userId));

        // Default to OTHER type if not specified
        return uploadFile(file, folder, user, com.carselling.oldcar.model.ResourceType.OTHER, userId);
    }

    /**
     * Upload multiple files
     */
    public List<FileUploadResponse> uploadMultipleFiles(List<MultipartFile> files, String folder,
            com.carselling.oldcar.model.User uploader,
            com.carselling.oldcar.model.ResourceType ownerType,
            Long ownerId) throws IOException {
        List<FileUploadResponse> responses = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                FileUploadResponse response = uploadFile(file, folder, uploader, ownerType, ownerId);
                responses.add(response);
            } catch (Exception e) {
                log.error("Error uploading file {}: {}", file.getOriginalFilename(), e.getMessage());
                // Continue with other files, but log the error
            }
        }

        return responses;
    }

    /**
     * Legacy multiple upload (Deprecated)
     */
    public List<FileUploadResponse> uploadMultipleFiles(List<MultipartFile> files, String folder, Long userId)
            throws IOException {
        com.carselling.oldcar.model.User user = userRepository.findById(userId)
                .orElseThrow(() -> new IOException("User not found: " + userId));

        return uploadMultipleFiles(files, folder, user, com.carselling.oldcar.model.ResourceType.OTHER, userId);
    }

    /**
     * Delete file from Firebase
     */
    public boolean deleteFile(String fileUrl) {
        try {
            firebaseStorageService.deleteFile(fileUrl);
            log.info("File deleted successfully: {}", fileUrl);
            return true;
        } catch (Exception e) {
            log.error("Error deleting file from Firebase: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Generate presigned URL for secure file access
     * Note: Firebase public URLs are persistent if made public.
     * For now returning the original URL as it is practically accessible.
     */
    public String generatePresignedUrl(String fileUrl, int expirationMinutes) {
        return fileUrl;
    }

    /**
     * Get file metadata
     * Note: Current basic implementation returns basic info from what we can infer
     * or fetch.
     * To fully implement, we would need to fetch generic metadata from Firebase.
     */
    public FileMetadata getFileMetadata(String fileUrl) {
        // Stub implementation - in a real scenario we'd query Firebase Storage for Blob
        // metadata
        return FileMetadata.builder()
                .contentType("application/octet-stream") // Default or fetched
                .contentLength(0)
                .lastModified(new Date())
                .userMetadata(new HashMap<>())
                .build();
    }

    // ========================= PRIVATE HELPER METHODS =========================

    /**
     * Validate uploaded file
     */
    private void validateFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IOException("Unable to determine file type");
        }

        // Check file type
        String fileCategory = getFileCategory(contentType);
        if (fileCategory == null) {
            // Strict check disabled for now? Or keep enabled.
            // keeping it enabled as per original code
            throw new IOException("File type not allowed: " + contentType);
        }

        // Check file size
        long maxSize = getMaxFileSize(fileCategory);
        if (file.getSize() > maxSize) {
            throw new IOException("File size exceeds limit. Max allowed: " + (maxSize / 1024 / 1024) + "MB");
        }

        // Additional validation for specific file types
        if ("image".equals(fileCategory)) {
            validateImageFile(file);
        }
    }

    /**
     * Get file category based on content type
     */
    private String getFileCategory(String contentType) {
        for (Map.Entry<String, Set<String>> entry : ALLOWED_FILE_TYPES.entrySet()) {
            if (entry.getValue().contains(contentType)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Get maximum file size for category
     */
    private long getMaxFileSize(String category) {
        switch (category) {
            case "image":
                return MAX_IMAGE_SIZE;
            case "document":
                return MAX_DOCUMENT_SIZE;
            case "video":
                return MAX_VIDEO_SIZE;
            default:
                return MAX_DOCUMENT_SIZE;
        }
    }

    /**
     * Validate image file (additional checks)
     */
    private void validateImageFile(MultipartFile file) throws IOException {
        // Check if file has valid image extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String extension = getFileExtension(originalFilename).toLowerCase();
            if (!Set.of("jpg", "jpeg", "png", "gif", "webp").contains(extension)) {
                throw new IOException("Invalid image file extension: " + extension);
            }
        }
    }

    /**
     * Generate unique file name
     */
    private String generateUniqueFileName(String originalFilename, Long userId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(originalFilename);

        return String.format("%d_%s_%s.%s", userId, timestamp, uuid, extension);
    }

    /**
     * Extract file extension
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
