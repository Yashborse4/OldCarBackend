package com.carselling.oldcar.dto.car;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CarDocumentDto {
    private Long id;
    private Long carId;
    private Long userId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String mimeType;
    private String documentType;
    private String description;
    private Boolean isVerified;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
}
