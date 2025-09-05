package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Controller for file upload and management operations
 */
@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileUploadService fileUploadService;

    /**
     * Upload single file
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "general") String folder,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            
            FileUploadResponse response = fileUploadService.uploadFile(file, folder, currentUser.getId());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "File upload failed", "message", e.getMessage()));
        }
    }

    /**
     * Upload multiple files
     */
    @PostMapping("/upload/multiple")
    public ResponseEntity<?> uploadMultipleFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "folder", defaultValue = "general") String folder,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            
            List<FileUploadResponse> responses = fileUploadService.uploadMultipleFiles(files, folder, currentUser.getId());
            
            return ResponseEntity.ok(Map.of(
                "message", "Files uploaded successfully",
                "uploadedFiles", responses,
                "totalFiles", files.size(),
                "successfulUploads", responses.size()
            ));
        } catch (Exception e) {
            log.error("Error uploading multiple files: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Multiple file upload failed", "message", e.getMessage()));
        }
    }

    /**
     * Upload car images
     */
    @PostMapping("/upload/car-images")
    public ResponseEntity<?> uploadCarImages(
            @RequestParam("images") List<MultipartFile> images,
            @RequestParam("carId") Long carId,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            String folder = "cars/" + carId + "/images";
            
            List<FileUploadResponse> responses = fileUploadService.uploadMultipleFiles(images, folder, currentUser.getId());
            
            return ResponseEntity.ok(Map.of(
                "message", "Car images uploaded successfully",
                "carId", carId,
                "uploadedImages", responses
            ));
        } catch (Exception e) {
            log.error("Error uploading car images: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Car images upload failed", "message", e.getMessage()));
        }
    }

    /**
     * Upload profile picture
     */
    @PostMapping("/upload/profile-picture")
    public ResponseEntity<?> uploadProfilePicture(
            @RequestParam("image") MultipartFile image,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            String folder = "users/" + currentUser.getId() + "/profile";
            
            FileUploadResponse response = fileUploadService.uploadFile(image, folder, currentUser.getId());
            
            return ResponseEntity.ok(Map.of(
                "message", "Profile picture uploaded successfully",
                "profilePicture", response
            ));
        } catch (Exception e) {
            log.error("Error uploading profile picture: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Profile picture upload failed", "message", e.getMessage()));
        }
    }

    /**
     * Upload chat attachment
     */
    @PostMapping("/upload/chat-attachment")
    public ResponseEntity<?> uploadChatAttachment(
            @RequestParam("file") MultipartFile file,
            @RequestParam("chatRoomId") Long chatRoomId,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            String folder = "chat/" + chatRoomId + "/attachments";
            
            FileUploadResponse response = fileUploadService.uploadFile(file, folder, currentUser.getId());
            
            return ResponseEntity.ok(Map.of(
                "message", "Chat attachment uploaded successfully",
                "chatRoomId", chatRoomId,
                "attachment", response
            ));
        } catch (Exception e) {
            log.error("Error uploading chat attachment: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Chat attachment upload failed", "message", e.getMessage()));
        }
    }

    /**
     * Delete file
     */
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteFile(
            @RequestParam("fileUrl") String fileUrl,
            Authentication authentication) {
        try {
            boolean deleted = fileUploadService.deleteFile(fileUrl);
            
            if (deleted) {
                return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "File not found or could not be deleted"));
            }
        } catch (Exception e) {
            log.error("Error deleting file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "File deletion failed", "message", e.getMessage()));
        }
    }

    /**
     * Generate presigned URL for secure access
     */
    @GetMapping("/presigned-url")
    public ResponseEntity<?> generatePresignedUrl(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(value = "expirationMinutes", defaultValue = "60") int expirationMinutes,
            Authentication authentication) {
        try {
            String presignedUrl = fileUploadService.generatePresignedUrl(fileUrl, expirationMinutes);
            
            return ResponseEntity.ok(Map.of(
                "originalUrl", fileUrl,
                "presignedUrl", presignedUrl,
                "expirationMinutes", expirationMinutes
            ));
        } catch (Exception e) {
            log.error("Error generating presigned URL: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Failed to generate presigned URL", "message", e.getMessage()));
        }
    }

    /**
     * Get file metadata
     */
    @GetMapping("/metadata")
    public ResponseEntity<?> getFileMetadata(
            @RequestParam("fileUrl") String fileUrl,
            Authentication authentication) {
        try {
            var metadata = fileUploadService.getFileMetadata(fileUrl);
            
            if (metadata != null) {
                return ResponseEntity.ok(Map.of(
                    "fileUrl", fileUrl,
                    "contentType", metadata.getContentType(),
                    "contentLength", metadata.getContentLength(),
                    "lastModified", metadata.getLastModified(),
                    "userMetadata", metadata.getUserMetadata()
                ));
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "File metadata not found"));
            }
        } catch (Exception e) {
            log.error("Error getting file metadata: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Failed to get file metadata", "message", e.getMessage()));
        }
    }

    /**
     * Health check endpoint for file service
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "service", "File Upload Service",
            "status", "UP",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
