package com.carselling.oldcar.dto.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class DirectUploadDTOs {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitRequest {
        private String fileName;
        private String folder; // e.g., "cars/123/images"
        private String contentType;
        private Long contentLength; // Optional: for pre-upload size validation
        private Long carId; // Optional: for context/auth checks when uploading car media
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InitResponse {
        private String uploadUrl;
        private String authorizationToken;
        private String fileName; // The unique file name to use in X-Bz-File-Name header
        private String fileUrl; // The future public URL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteRequest {
        private String b2FileName; // The unique name given by init
        private String fileId; // Returned by B2
        private Long fileSize;
        private String folder; // For context/auth checks
        private Long carId; // Optional context for easier auth
        private String originalFileName;
        private String contentType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteResponse {
        private String fileUrl;
        private String fileName;
        private Long id; // DB ID of TemporaryFile or UploadedFile
        private boolean success;
    }
}
