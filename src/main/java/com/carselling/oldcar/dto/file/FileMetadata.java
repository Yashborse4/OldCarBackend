package com.carselling.oldcar.dto.file;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadata {

    private String contentType;
    private Long contentLength;
    private LocalDateTime lastModified;
    private Map<String, String> userMetadata;
}

