package com.carselling.oldcar.service;

import com.carselling.oldcar.config.FileUploadConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Service for validating file uploads for security
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileValidationService {

    private final FileUploadConfig fileUploadConfig;
    private final Tika tika = new Tika();

    /**
     * Validate a file upload for security issues
     */
    public void validateFile(MultipartFile file) throws SecurityException {
        if (file == null || file.isEmpty()) {
            throw new SecurityException("File is empty or null");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new SecurityException("Invalid filename");
        }

        // Check file size
        validateFileSize(file);

        // Validate file extension
        if (fileUploadConfig.isValidateFileExtension()) {
            validateFileExtension(originalFilename);
        }

        // Validate content type
        if (fileUploadConfig.isValidateContentType()) {
            validateContentType(file);
        }

        // Check for potential security threats
        checkForSecurityThreats(originalFilename);
    }

    /**
     * Validate multiple files
     */
    public void validateFiles(List<MultipartFile> files) throws SecurityException {
        if (files.size() > fileUploadConfig.getMaxFilesPerRequest()) {
            throw new SecurityException(
                "Too many files. Maximum allowed: " + fileUploadConfig.getMaxFilesPerRequest()
            );
        }

        for (MultipartFile file : files) {
            validateFile(file);
        }
    }

    private void validateFileSize(MultipartFile file) throws SecurityException {
        long maxFileSizeBytes = fileUploadConfig.getMaxFileSizeMB() * 1024L * 1024L;
        if (file.getSize() > maxFileSizeBytes) {
            throw new SecurityException(
                "File size exceeds maximum allowed size of " + fileUploadConfig.getMaxFileSizeMB() + "MB"
            );
        }
    }

    private void validateFileExtension(String filename) throws SecurityException {
        String extension = getFileExtension(filename);
        if (extension == null) {
            throw new SecurityException("File has no extension");
        }

        extension = extension.toLowerCase();
        if (!fileUploadConfig.getAllowedExtensions().contains(extension)) {
            throw new SecurityException(
                "File extension not allowed. Allowed extensions: " + fileUploadConfig.getAllowedExtensions()
            );
        }
    }

    private void validateContentType(MultipartFile file) throws SecurityException {
        try {
            String detectedContentType = tika.detect(file.getBytes());
            String declaredContentType = file.getContentType();

            log.debug("Detected content type: {}, Declared: {}", detectedContentType, declaredContentType);

            // Check if detected content type is allowed
            if (!isAllowedContentType(detectedContentType)) {
                throw new SecurityException(
                    "Content type not allowed: " + detectedContentType + 
                    ". Allowed types: " + fileUploadConfig.getAllowedContentTypes()
                );
            }

            // Verify declared type matches detected type
            if (declaredContentType != null && !declaredContentType.equals(detectedContentType)) {
                log.warn("Content type mismatch: declared={}, detected={}", declaredContentType, detectedContentType);
            }

        } catch (IOException e) {
            log.error("Error detecting file content type", e);
            throw new SecurityException("Unable to determine file content type");
        }
    }

    private void checkForSecurityThreats(String filename) throws SecurityException {
        // Check for path traversal attempts
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new SecurityException("Invalid filename: path traversal detected");
        }

        // Check for null bytes
        if (filename.contains("\0")) {
            throw new SecurityException("Invalid filename: null byte detected");
        }

        // Check for executable files
        String lowerFilename = filename.toLowerCase();
        List<String> dangerousExtensions = Arrays.asList(
            "exe", "bat", "cmd", "com", "pif", "scr", "vbs", "js", "jar", "sh"
        );

        for (String dangerousExt : dangerousExtensions) {
            if (lowerFilename.endsWith("." + dangerousExt)) {
                throw new SecurityException("Executable files are not allowed");
            }
        }

        // Check for double extensions
        if (filename.matches(".*\\.[^.]*\\.[^.]*")) {
            log.warn("Potential double extension attack: {}", filename);
        }
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1);
        }
        return null;
    }

    private boolean isAllowedContentType(String contentType) {
        if (contentType == null) {
            return false;
        }

        return fileUploadConfig.getAllowedContentTypes().stream()
            .anyMatch(allowed -> contentType.startsWith(allowed.split("/")[0] + "/") ||
                                contentType.equals(allowed));
    }

    /**
     * Get file size in human readable format
     */
    public String getFileSizeDisplay(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
