package com.carselling.oldcar.service.file;

import com.carselling.oldcar.config.FileUploadConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Service to initialize required storage directories on application startup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FolderInitializationService {

    private final FileUploadConfig fileUploadConfig;

    @Value("${file.upload.temp-directory:/tmp/car-uploads}")
    private String tempFileStorageDirectory;

    @PostConstruct
    public void init() {
        log.info("Starting folder initialization...");

        String basePath = fileUploadConfig.getPath();
        
        // 0. Initialize Base Path itself
        if (basePath != null && !basePath.isBlank()) {
            createDirectory(Paths.get(basePath));
        }

        // List of subfolders to ensure exist
        List<String> subFolders = List.of(
                "temp",
                "cars",
                "dealers",
                "chat",
                "users"
        );

        // 1. Initialize Base Path subfolders
        for (String folder : subFolders) {
            createDirectory(Paths.get(basePath, folder));
        }

        // 2. Initialize Temp File Storage Directory (secondary temp if configured)
        if (tempFileStorageDirectory != null && !tempFileStorageDirectory.isBlank()) {
            createDirectory(Paths.get(tempFileStorageDirectory));
        }

        log.info("Folder initialization completed.");
    }

    private void createDirectory(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created directory: {}", path.toAbsolutePath());
            } else {
                log.debug("Directory already exists: {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create directory: {}. Error: {}", path.toAbsolutePath(), e.getMessage());
            // We don't throw exception here to allow the app to start, 
            // but subsequent file operations will fail if folders are missing.
        }
    }
}
