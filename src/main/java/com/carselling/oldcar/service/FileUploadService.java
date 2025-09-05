package com.carselling.oldcar.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.carselling.oldcar.config.S3Config;
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
 * Service for handling file uploads to AWS S3
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileUploadService {

    private final AmazonS3 amazonS3Client;
    private final S3Config s3Config;

    // Allowed file types and their MIME types
    private static final Map<String, Set<String>> ALLOWED_FILE_TYPES = Map.of(
        "image", Set.of("image/jpeg", "image/png", "image/gif", "image/webp"),
        "document", Set.of("application/pdf", "application/msword", 
                          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                          "text/plain"),
        "video", Set.of("video/mp4", "video/avi", "video/mov", "video/wmv")
    );

    // Maximum file sizes (in bytes)
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_DOCUMENT_SIZE = 20 * 1024 * 1024; // 20MB
    private static final long MAX_VIDEO_SIZE = 100 * 1024 * 1024; // 100MB

    /**
     * Upload file to S3 and return file URL
     */
    public FileUploadResponse uploadFile(MultipartFile file, String folder, Long userId) throws IOException {
        // Validate file
        validateFile(file);

        String fileName = generateUniqueFileName(file.getOriginalFilename(), userId);
        String key = folder + "/" + fileName;

        try {
            // Create metadata
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());
            metadata.addUserMetadata("original-filename", file.getOriginalFilename());
            metadata.addUserMetadata("uploaded-by", userId.toString());
            metadata.addUserMetadata("upload-timestamp", LocalDateTime.now().toString());

            // Upload file to S3
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                s3Config.getBucketName(), 
                key, 
                file.getInputStream(), 
                metadata
            );

            // Set public read permissions for images
            if (isImageFile(file)) {
                putObjectRequest.setCannedAcl(CannedAccessControlList.PublicRead);
            }

            amazonS3Client.putObject(putObjectRequest);

            // Generate file URL
            String fileUrl = amazonS3Client.getUrl(s3Config.getBucketName(), key).toString();

            log.info("File uploaded successfully: {} -> {}", file.getOriginalFilename(), fileUrl);

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
            log.error("Error uploading file to S3: {}", e.getMessage(), e);
            throw new IOException("Failed to upload file: " + e.getMessage());
        }
    }

    /**
     * Upload multiple files
     */
    public List<FileUploadResponse> uploadMultipleFiles(List<MultipartFile> files, String folder, Long userId) throws IOException {
        List<FileUploadResponse> responses = new ArrayList<>();
        
        for (MultipartFile file : files) {
            try {
                FileUploadResponse response = uploadFile(file, folder, userId);
                responses.add(response);
            } catch (Exception e) {
                log.error("Error uploading file {}: {}", file.getOriginalFilename(), e.getMessage());
                // Continue with other files, but log the error
            }
        }
        
        return responses;
    }

    /**
     * Delete file from S3
     */
    public boolean deleteFile(String fileUrl) {
        try {
            String key = extractKeyFromUrl(fileUrl);
            amazonS3Client.deleteObject(s3Config.getBucketName(), key);
            log.info("File deleted successfully: {}", fileUrl);
            return true;
        } catch (Exception e) {
            log.error("Error deleting file from S3: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Generate presigned URL for secure file access
     */
    public String generatePresignedUrl(String fileUrl, int expirationMinutes) {
        try {
            String key = extractKeyFromUrl(fileUrl);
            Date expiration = new Date();
            long expTimeMillis = expiration.getTime() + (expirationMinutes * 60 * 1000L);
            expiration.setTime(expTimeMillis);

            GeneratePresignedUrlRequest generatePresignedUrlRequest = 
                new GeneratePresignedUrlRequest(s3Config.getBucketName(), key)
                    .withMethod(HttpMethod.GET)
                    .withExpiration(expiration);

            return amazonS3Client.generatePresignedUrl(generatePresignedUrlRequest).toString();
        } catch (Exception e) {
            log.error("Error generating presigned URL: {}", e.getMessage(), e);
            return fileUrl; // Return original URL as fallback
        }
    }

    /**
     * Get file metadata
     */
    public ObjectMetadata getFileMetadata(String fileUrl) {
        try {
            String key = extractKeyFromUrl(fileUrl);
            return amazonS3Client.getObjectMetadata(s3Config.getBucketName(), key);
        } catch (Exception e) {
            log.error("Error getting file metadata: {}", e.getMessage(), e);
            return null;
        }
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
            case "image": return MAX_IMAGE_SIZE;
            case "document": return MAX_DOCUMENT_SIZE;
            case "video": return MAX_VIDEO_SIZE;
            default: return MAX_DOCUMENT_SIZE;
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
     * Check if file is an image
     */
    private boolean isImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
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

    /**
     * Extract S3 key from file URL
     */
    private String extractKeyFromUrl(String fileUrl) {
        // Extract key from S3 URL format
        // Example: https://bucket.s3.region.amazonaws.com/folder/file.jpg
        try {
            String bucketUrl = "https://" + s3Config.getBucketName() + ".s3.";
            if (fileUrl.contains(bucketUrl)) {
                int keyStart = fileUrl.indexOf(".com/") + 5;
                return fileUrl.substring(keyStart);
            }
            
            // Alternative format: https://s3.region.amazonaws.com/bucket/folder/file.jpg
            String s3Url = "https://s3.";
            if (fileUrl.contains(s3Url)) {
                int bucketStart = fileUrl.indexOf(s3Config.getBucketName());
                if (bucketStart > 0) {
                    int keyStart = bucketStart + s3Config.getBucketName().length() + 1;
                    return fileUrl.substring(keyStart);
                }
            }
            
            throw new IllegalArgumentException("Invalid S3 URL format");
        } catch (Exception e) {
            log.error("Error extracting key from URL: {}", fileUrl, e);
            throw new IllegalArgumentException("Invalid S3 URL: " + fileUrl);
        }
    }
}
