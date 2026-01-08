package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.file.FileMetadata;
import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.model.UploadedFile;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.UploadedFileRepository;
import com.carselling.oldcar.repository.UserRepository;
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
    private final UploadedFileRepository uploadedFileRepository;
    private final UserRepository userRepository;
    private final FileValidationService fileValidationService;

    // Validation constants moved to FileUploadConfig/FileValidationService

    /**
     * Upload file with strict ownership tracking
     */
    public FileUploadResponse uploadFile(MultipartFile file, String folder, User uploader,
            ResourceType ownerType, Long ownerId) throws IOException {
        // Validate file
        // Validate file
        fileValidationService.validateFile(file);

        String fileName = generateUniqueFileName(file.getOriginalFilename(), uploader.getId());
        String fullPath = folder + "/" + fileName;

        try {
            // Upload file to Firebase
            String fileUrl = firebaseStorageService.uploadFile(file, fullPath);

            log.info("File uploaded successfully: {} -> {}", file.getOriginalFilename(), fileUrl);

            // Save metadata to DB
            UploadedFile uploadedFile = UploadedFile.builder()
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
                    .originalFileName(sanitizeFileName(file.getOriginalFilename()))
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IOException("User not found: " + userId));

        // Default to OTHER type if not specified
        return uploadFile(file, folder, user, ResourceType.OTHER, userId);
    }

    /**
     * Upload multiple files
     */
    public List<FileUploadResponse> uploadMultipleFiles(List<MultipartFile> files, String folder,
            User uploader,
            ResourceType ownerType,
            Long ownerId) throws IOException {
        List<FileUploadResponse> responses = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                FileUploadResponse response = uploadFile(file, folder, uploader, ownerType, ownerId);
                responses.add(response);
            } catch (Exception e) {
                log.error("Error uploading file {}: {}", file.getOriginalFilename(), e.getMessage());
                // Return failure response instead of skipping
                responses.add(FileUploadResponse.builder()
                        .originalFileName(file.getOriginalFilename())
                        .success(false)
                        .message("Failed to upload: " + e.getMessage())
                        .build());
            }
        }

        return responses;
    }

    /**
     * Legacy multiple upload (Deprecated)
     */
    public List<FileUploadResponse> uploadMultipleFiles(List<MultipartFile> files, String folder, Long userId)
            throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IOException("User not found: " + userId));

        return uploadMultipleFiles(files, folder, user, ResourceType.OTHER, userId);
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
        String fileName = getFileNameFromUrl(fileUrl);
        if (fileName != null) {
            String signedUrl = firebaseStorageService.generateSignedUrl(fileName, expirationMinutes);
            return signedUrl != null ? signedUrl : fileUrl;
        }
        return fileUrl;
    }

    private String getFileNameFromUrl(String fileUrl) {
        // Basic extraction, assuming standard GCS public URL structure
        // https://storage.googleapis.com/<bucket>/<filename>
        if (fileUrl != null && fileUrl.contains("storage.googleapis.com")) {
            return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        }
        return null;
    }

    /**
     * Get file metadata
     * Note: Current basic implementation returns basic info from what we can infer
     * or fetch.
     * To fully implement, we would need to fetch generic metadata from Firebase.
     */
    public FileMetadata getFileMetadata(String fileUrl) {
        String fileName = getFileNameFromUrl(fileUrl);
        if (fileName != null) {
            FileMetadata metadata = firebaseStorageService.getFileMetadata(fileName);
            if (metadata != null) {
                return metadata;
            }
        }

        // Fallback or empty if not found/error
        return FileMetadata.builder().build();
    }

    // ========================= PRIVATE HELPER METHODS =========================

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

    /**
     * Sanitize filename to prevent XSS in response
     */
    private String sanitizeFileName(String filename) {
        if (filename == null)
            return null;

        // Strict whitelist: alphanumeric, dots, underscores, hyphens only
        String safeName = filename.replaceAll("[^a-zA-Z0-9._-]", "_");

        // Prevent directory traversal
        safeName = safeName.replace("..", "");

        // Max length Limit
        if (safeName.length() > 200) {
            safeName = safeName.substring(0, 196) + getFileExtension(filename);
        }

        return safeName;
    }
}
