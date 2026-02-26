package com.carselling.oldcar.service.media;

import com.carselling.oldcar.dto.file.DirectUploadDTOs;
import com.carselling.oldcar.dto.file.FileUploadResponse;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Service interface for handling all media-related operations.
 * Segregates media logic from controllers.
 */
public interface MediaService {

    /**
     * Upload a single file.
     */
    FileUploadResponse uploadSingleFile(MultipartFile file, String folder, String checksum, Long userId);

    /**
     * Upload multiple files.
     */
    Map<String, Object> uploadMultipleFiles(List<MultipartFile> files, String folder, Long userId);

    /**
     * Upload car images specifically (handles processing trigger).
     */
    Map<String, Object> uploadCarImages(List<MultipartFile> images, Long carId, Long userId);

    /**
     * Initialize a direct upload (e.g. to B2).
     */
    DirectUploadDTOs.InitResponse initDirectUpload(DirectUploadDTOs.InitRequest request, Long userId);

    /**
     * Complete a direct upload.
     */
    DirectUploadDTOs.CompleteResponse completeDirectUpload(DirectUploadDTOs.CompleteRequest request, Long userId);

    /**
     * Delete a file by URL.
     */
    void deleteFile(String fileUrl, Long userId);

    /**
     * Generate a presigned URL for a file.
     */
    String generatePresignedUrl(String fileUrl, int expirationMinutes, Long userId);

    /**
     * Get metadata for a file.
     */
    Map<String, Object> getFileMetadata(String fileUrl, Long userId);

    /**
     * Get a media file with access checks.
     * Returns either the direct URL (public) or a presigned URL (private).
     */
    String getMediaFileUrl(Long id, Long userId);
}
//
