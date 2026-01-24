package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.file.FileMetadata;
import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.model.ResourceType;

import com.carselling.oldcar.model.User;

import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

import java.util.*;

/**
 * Service for handling file uploads to Backblaze B2 Storage
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    private final com.carselling.oldcar.b2.B2FileService b2FileService;
    private final UserRepository userRepository;

    // Validation constants moved to FileUploadConfig/FileValidationService

    /**
     * Upload file with strict ownership tracking
     */
    public FileUploadResponse uploadFile(MultipartFile file, String folder, User uploader,
            ResourceType ownerType, Long ownerId) throws IOException {
        // Delegate to B2
        return b2FileService.uploadFile(file, folder, uploader, ownerType, ownerId);
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
        return b2FileService.uploadMultipleFiles(files, folder, uploader, ownerType, ownerId);
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
     * Delete file
     */
    public boolean deleteFile(String fileUrl) {
        return b2FileService.deleteFile(fileUrl);
    }

    /**
     * Generate presigned URL for secure file access
     * B2 usage: Public URLs are permanent.
     */
    public String generatePresignedUrl(String fileUrl, int expirationMinutes) {
        return fileUrl;
    }

    public FileMetadata getFileMetadata(String fileUrl) {
        return FileMetadata.builder().build();
    }
}
