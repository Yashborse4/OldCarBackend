package com.carselling.oldcar.dto.file;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * Response DTO for file upload operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    private String fileName;
    private String originalFileName;
    private String fileUrl;
    private Long fileSize;
    private String contentType;
    private String folder;
    private LocalDateTime uploadedAt;
    private String presignedUrl;

    // Additional metadata
    private String fileCategory; // image, document, video
    private Boolean isPublic;
    private String thumbnailUrl; // For images

    // Status for bulk uploads
    @Builder.Default
    private boolean success = true;
    private String message;

    // Constructor for builder pattern
    // Constructor for builder pattern handled by @AllArgsConstructor

    // Helper methods
    public String getFormattedFileSize() {
        if (fileSize == null)
            return "0 B";

        long bytes = fileSize;
        if (bytes < 1024)
            return bytes + " B";

        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";

        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public boolean isImageFile() {
        return contentType != null && contentType.startsWith("image/");
    }

    public boolean isDocumentFile() {
        return contentType != null && (contentType.equals("application/pdf") ||
                contentType.equals("application/msword") ||
                contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                contentType.equals("text/plain"));
    }

    public boolean isVideoFile() {
        return contentType != null && contentType.startsWith("video/");
    }
}
