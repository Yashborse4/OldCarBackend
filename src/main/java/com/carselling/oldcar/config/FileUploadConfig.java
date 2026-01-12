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
    private int maxFileSizeMB = 10;

    @Max(200)
    @Min(1)
    private int maxVideoSizeMB = 100;

    @Max(100)
    @Min(1)
    private int maxRequestSizeMB = 50;

    @Max(10)
    @Min(1)
    private int maxFilesPerRequest = 10;

    private List<String> allowedContentTypes = List.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "application/pdf", "text/plain",
            "video/mp4", "video/quicktime", "video/x-msvideo");

    private List<String> allowedExtensions = List.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "txt", "mp4", "mov", "avi");

    private boolean scanForViruses = true;
    private boolean validateContentType = true;
    private boolean validateFileExtension = true;

    // Getters and Setters
    public int getMaxFileSizeMB() {
        return maxFileSizeMB;
    }

    public void setMaxFileSizeMB(int maxFileSizeMB) {
        this.maxFileSizeMB = maxFileSizeMB;
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
}
