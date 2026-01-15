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
public class InitUploadResponse {
    private String sessionId;
    private List<String> uploadUrls;
    private List<String> filePaths;
}
