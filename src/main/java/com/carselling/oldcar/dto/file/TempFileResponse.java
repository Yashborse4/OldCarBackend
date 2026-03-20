package com.carselling.oldcar.dto.file;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for temporary file uploads.
 * Prevents LazyInitializationException by not exposing
 * the User entity (and its lazy collections like cars).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TempFileResponse {

    private Long id;
    private String fileUrl;
    private String fileId;
    private String fileName;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private Long carId;
    private String storageStatus;
    private LocalDateTime createdAt;

    /**
     * Convert a TemporaryFile entity to this DTO.
     *
     * @param entity The TemporaryFile entity to convert.
     * @return A TempFileResponse DTO with safe, serializable fields.
     */
    public static TempFileResponse fromEntity(com.carselling.oldcar.model.TemporaryFile entity) {
        return TempFileResponse.builder()
                .id(entity.getId())
                .fileUrl(entity.getFileUrl())
                .fileId(entity.getFileId())
                .fileName(entity.getFileName())
                .originalFileName(entity.getOriginalFileName())
                .contentType(entity.getContentType())
                .fileSize(entity.getFileSize())
                .carId(entity.getCarId())
                .storageStatus(entity.getStorageStatus() != null
                        ? entity.getStorageStatus().name()
                        : null)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
