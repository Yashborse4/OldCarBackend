package com.carselling.oldcar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * Configuration for file upload security and limits
 */
@Configuration
@ConfigurationProperties(prefix = "app.upload")
@Validated
public class FileUploadConfig {

    @Max(50)
    @Min(1)
    private int maxFileSizeMB = 3; // Updated to 3MB

    @Max(20)
    @Min(1)
    private int maxImageSizeMB = 5; // Default 5MB for images

    @Max(500)
    @Min(1)
    private int maxVideoSizeMB = 250; // Updated to 300MB

    @Max(100)
    @Min(1)
    private int maxRequestSizeMB = 50;

    @Max(10)
    @Min(1)
    private int maxFilesPerRequest = 10;

    private List<String> allowedContentTypes = List.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "application/pdf", "text/plain",
            // Video types - comprehensive list
            "video/mp4", "video/quicktime", "video/x-msvideo", "video/x-matroska",
            "video/webm", "video/3gpp", "video/3gpp2", "video/x-flv",
            "video/x-ms-wmv", "video/x-m4v", "video/mpeg", "video/hevc");

    private List<String> allowedExtensions = List.of(
            "jpg", "jpeg", "png", "gif", "webp",
            // Video extensions - comprehensive list
            "mp4", "mov", "avi", "mkv", "webm", "3gp", "flv", "wmv", "m4v", "mpeg", "mpg");

    private List<String> allowedImageExtensions = List.of("jpg", "jpeg", "png", "gif", "webp");
    private List<String> allowedVideoExtensions = List.of("mp4", "mov", "avi", "mkv", "webm", "3gp", "flv", "wmv",
            "m4v", "mpeg", "mpg");

    private boolean scanForViruses = true;
    private boolean validateContentType = true;
    private boolean validateFileExtension = true;
    private boolean validateMagicNumbers = true;

    // Getters and Setters
    public int getMaxFileSizeMB() {
        return maxFileSizeMB;
    }

    public void setMaxFileSizeMB(int maxFileSizeMB) {
        this.maxFileSizeMB = maxFileSizeMB;
    }

    public int getMaxImageSizeMB() {
        return maxImageSizeMB;
    }

    public void setMaxImageSizeMB(int maxImageSizeMB) {
        this.maxImageSizeMB = maxImageSizeMB;
    }

    public int getMaxVideoSizeMB() {
        return maxVideoSizeMB;
    }

    public void setMaxVideoSizeMB(int maxVideoSizeMB) {
        this.maxVideoSizeMB = maxVideoSizeMB;
    }

    public int getMaxRequestSizeMB() {
        return maxRequestSizeMB;
    }

    public void setMaxRequestSizeMB(int maxRequestSizeMB) {
        this.maxRequestSizeMB = maxRequestSizeMB;
    }

    public int getMaxFilesPerRequest() {
        return maxFilesPerRequest;
    }

    public void setMaxFilesPerRequest(int maxFilesPerRequest) {
        this.maxFilesPerRequest = maxFilesPerRequest;
    }

    public List<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(List<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public boolean isScanForViruses() {
        return scanForViruses;
    }

    public void setScanForViruses(boolean scanForViruses) {
        this.scanForViruses = scanForViruses;
    }

    public boolean isValidateContentType() {
        return validateContentType;
    }

    public void setValidateContentType(boolean validateContentType) {
        this.validateContentType = validateContentType;
    }

    public boolean isValidateFileExtension() {
        return validateFileExtension;
    }

    public void setValidateFileExtension(boolean validateFileExtension) {
        this.validateFileExtension = validateFileExtension;
    }

    public List<String> getAllowedImageExtensions() {
        return allowedImageExtensions;
    }

    public void setAllowedImageExtensions(List<String> allowedImageExtensions) {
        this.allowedImageExtensions = allowedImageExtensions;
    }

    public List<String> getAllowedVideoExtensions() {
        return allowedVideoExtensions;
    }

    public void setAllowedVideoExtensions(List<String> allowedVideoExtensions) {
        this.allowedVideoExtensions = allowedVideoExtensions;
    }

    public boolean isValidateMagicNumbers() {
        return validateMagicNumbers;
    }

    public void setValidateMagicNumbers(boolean validateMagicNumbers) {
        this.validateMagicNumbers = validateMagicNumbers;
    }
}
