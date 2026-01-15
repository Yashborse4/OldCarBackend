package com.carselling.oldcar.dto.media;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadRequest {
    private Long carId;
    private String sessionId;
    private boolean success;
    private List<String> uploadedFilePaths;
}
