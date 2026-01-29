package com.carselling.oldcar.dto.car;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * Request DTO for media upload (Direct-to-storage flow)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaUploadRequest {
    private List<String> images;
    private String videoUrl;
}
