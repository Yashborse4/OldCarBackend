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

        // Scan for viruses if enabled
        if (fileUploadConfig.isScanForViruses()) {
            scanForViruses(file);
        }

        // Validate image dimensions if it's an image
        if (isImageFile(originalFilename)) {
            validateImageDimensions(file);
        }
    }

    /**
     * Validate multiple files
     */
    public void validateFiles(List<MultipartFile> files) throws SecurityException {
        if (files.size() > fileUploadConfig.getMaxFilesPerRequest()) {
            throw new SecurityException(
                    "Too many files. Maximum allowed: " + fileUploadConfig.getMaxFilesPerRequest());
        }

        for (MultipartFile file : files) {
            validateFile(file);
        }
    }

    private void validateFileSize(MultipartFile file) throws SecurityException {
        // Different limits for Video vs Other files
        boolean isVideo = isVideoFile(file.getOriginalFilename());
        long maxFileSizeBytes;

        if (isVideo) {
            maxFileSizeBytes = fileUploadConfig.getMaxVideoSizeMB() * 1024L * 1024L;
        } else if (isImageFile(file.getOriginalFilename())) {
            // 1.5 MB limit for images as per requirement (hardcoded or config driven, using
            // 1.5MB as requested)
            maxFileSizeBytes = 1500 * 1024L; // 1.5 MB
        } else {
            maxFileSizeBytes = fileUploadConfig.getMaxFileSizeMB() * 1024L * 1024L;
        }

        if (file.getSize() > maxFileSizeBytes) {
            String limitMsg = isVideo ? fileUploadConfig.getMaxVideoSizeMB() + "MB"
                    : (isImageFile(file.getOriginalFilename()) ? "1.5MB" : fileUploadConfig.getMaxFileSizeMB() + "MB");
            throw new SecurityException(
                    "File size exceeds maximum allowed size of " + limitMsg);
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
                    "File extension not allowed. Allowed extensions: " + fileUploadConfig.getAllowedExtensions());
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
                                ". Allowed types: " + fileUploadConfig.getAllowedContentTypes());
            }

            // Verify declared type matches detected type - STRICT Check
            if (declaredContentType != null && !declaredContentType.equals(detectedContentType)) {
                log.warn("Content type mismatch: declared={}, detected={}", declaredContentType, detectedContentType);
                // Strict enforcing:
                throw new SecurityException("Content type mismatch: declared " + declaredContentType + " but detected "
                        + detectedContentType);
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
                "exe", "bat", "cmd", "com", "pif", "scr", "vbs", "js", "jar", "sh");

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
        if (bytes < 1024)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Scan file for viruses and malware
     */
    private void scanForViruses(MultipartFile file) throws SecurityException {
        try {
            // Basic virus scanning - in production, integrate with actual antivirus service
            byte[] fileBytes = file.getBytes();

            // Check for common malicious signatures
            if (containsMaliciousSignatures(fileBytes)) {
                throw new SecurityException("File contains potential malware signatures");
            }

            // Check file entropy (high entropy might indicate encryption/obfuscation)
            if (hasHighEntropy(fileBytes)) {
                log.warn("File {} has high entropy, potential obfuscation detected", file.getOriginalFilename());
            }

            // Check for embedded scripts in images (polyglot files)
            if (isImageFile(file.getOriginalFilename()) && containsScriptContent(fileBytes)) {
                throw new SecurityException("Image file contains embedded scripts");
            }

        } catch (IOException e) {
            log.error("Error scanning file for viruses", e);
            throw new SecurityException("Unable to scan file for viruses");
        }
    }

    /**
     * Check if file contains known malicious signatures
     */
    private boolean containsMaliciousSignatures(byte[] fileBytes) {
        // Basic check for common malicious patterns
        String content = new String(fileBytes);
        String[] maliciousPatterns = {
                "<script", "javascript:", "vbscript:", "onload=", "onerror=",
                "eval(", "exec(", "system(", "shell_exec", "passthru",
                "<?php", "<%", "<iframe", "<object", "<embed",
                "cmd.exe", "/bin/sh", "/bin/bash", "powershell",
                "reflect.assembly", "wscript.shell",
                "base64_decode", "gzinflate", "str_rot13",
                "runtime.getruntime", "processbuilder"
        };

        String lowerContent = content.toLowerCase();
        for (String pattern : maliciousPatterns) {
            if (lowerContent.contains(pattern.toLowerCase())) {
                log.warn("Malicious pattern detected in file: {}", pattern);
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate file entropy to detect potential obfuscation
     */
    private boolean hasHighEntropy(byte[] fileBytes) {
        if (fileBytes.length == 0)
            return false;

        int[] frequency = new int[256];
        for (byte b : fileBytes) {
            frequency[b & 0xFF]++;
        }

        double entropy = 0.0;
        for (int freq : frequency) {
            if (freq > 0) {
                double probability = (double) freq / fileBytes.length;
                entropy -= probability * (Math.log(probability) / Math.log(2));
            }
        }

        // Entropy > 7.5 is suspicious for most file types
        return entropy > 7.5;
    }

    /**
     * Check if filename indicates a video file
     */
    private boolean isVideoFile(String filename) {
        String extension = getFileExtension(filename);
        return extension != null &&
                (extension.equalsIgnoreCase("mp4") || extension.equalsIgnoreCase("mov") ||
                        extension.equalsIgnoreCase("avi") || extension.equalsIgnoreCase("mkv") ||
                        extension.equalsIgnoreCase("webm"));
    }

    /**
     * Check if filename indicates an image file
     */
    private boolean isImageFile(String filename) {
        String extension = getFileExtension(filename);
        return extension != null &&
                (extension.equalsIgnoreCase("jpg") || extension.equalsIgnoreCase("jpeg") ||
                        extension.equalsIgnoreCase("png") || extension.equalsIgnoreCase("gif") ||
                        extension.equalsIgnoreCase("webp"));
    }

    /**
     * Check if image file contains script content
     */
    private boolean containsScriptContent(byte[] fileBytes) {
        String content = new String(fileBytes);
        String[] scriptIndicators = {
                "<script", "javascript:", "eval(", "function(", "alert(", "document."
        };

        for (String indicator : scriptIndicators) {
            if (content.toLowerCase().contains(indicator.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validate image dimensions for mobile performance
     */
    private void validateImageDimensions(MultipartFile file) throws SecurityException {
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(file.getInputStream());
            if (image == null) {
                // Not a valid image or format not supported by ImageIO
                // We let it pass if Tika said it was an image, but log warning
                log.warn("Could not parse image dimensions for validation: {}", file.getOriginalFilename());
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            // Max dimensions 4K usually enough
            int MAX_WIDTH = 4096;
            int MAX_HEIGHT = 4096;

            if (width > MAX_WIDTH || height > MAX_HEIGHT) {
                throw new SecurityException(String.format(
                        "Image dimensions too large (%dx%d). Max allowed is %dx%d",
                        width, height, MAX_WIDTH, MAX_HEIGHT));
            }

            // Min dimensions? Maybe 1x1 is fine, but 0x0 is invalid
            if (width < 1 || height < 1) {
                throw new SecurityException("Invalid image dimensions");
            }

        } catch (IOException e) {
            throw new SecurityException("Unable to validate image dimensions");
        }
    }

    /**
     * Validate file URL for security (SSRF, Path Traversal)
     */
    public void validateFileUrl(String url) throws SecurityException {
        if (url == null || url.isBlank()) {
            return;
        }

        // Allow Firebase Storage and Google Cloud Storage URLs
        boolean isTrusted = url.startsWith("https://storage.googleapis.com/") ||
                url.startsWith("https://firebasestorage.googleapis.com/") ||
                url.startsWith("https://lh3.googleusercontent.com/");

        if (!isTrusted) {
            log.warn("Rejected untrusted file URL: {}", url);
            throw new SecurityException("File URL must be from a trusted source (Firebase/GCS)");
        }

        // Additional security: check for path traversal attempts
        if (url.contains("..") || url.contains("%2e%2e") || url.contains("%252e")) {
            throw new SecurityException("Invalid file URL: path traversal detected");
        }
    }
}
