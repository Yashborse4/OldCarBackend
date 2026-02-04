package com.carselling.oldcar.service;

import com.carselling.oldcar.b2.B2Client;
import com.carselling.oldcar.model.TemporaryFile;
import com.carselling.oldcar.repository.TemporaryFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service to clean up abandoned temporary files.
 * Runs periodically to remove files that were uploaded but never finalized.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TempFileCleanupService {

    private final TemporaryFileRepository temporaryFileRepository;
    private final B2Client b2Client;

    /**
     * Run daily at 2 AM IST.
     * Deletes files older than 24 hours.
     */
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Kolkata")
    @Transactional
    public void cleanupAbandonedFiles() {
        log.info("Starting cleanup of abandoned temporary files...");

        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<TemporaryFile> abandonedFiles = temporaryFileRepository.findByCreatedAtBefore(threshold);

        if (abandonedFiles.isEmpty()) {
            log.info("No abandoned files found.");
            return;
        }

        log.info("Found {} abandoned files to delete.", abandonedFiles.size());

        for (TemporaryFile file : abandonedFiles) {
            try {
                // Delete from B2
                log.debug("Deleting file from B2: {} ({})", file.getFileName(), file.getFileId());
                try {
                    String fileNameInB2 = extractB2Name(file.getFileUrl());
                    b2Client.deleteFileVersion(fileNameInB2, file.getFileId());
                } catch (Exception e) {
                    log.warn(
                            "Failed to delete file from B2: {}. It might have inherently expired or be missing. Error: {}",
                            file.getFileId(), e.getMessage());
                    // Continue to delete from DB to keep metadata clean
                }

                // Delete from DB
                temporaryFileRepository.delete(file);
                log.debug("Deleted temporary file record: {}", file.getId());

            } catch (Exception e) {
                log.error("Failed to cleanup temporary file {}: {}", file.getId(), e.getMessage());
            }
        }

        log.info("Cleanup completed.");
    }

    private String extractB2Name(String fileUrl) {
        try {
            java.net.URI uri = new java.net.URI(fileUrl);
            String path = uri.getPath();
            if (path.startsWith("/"))
                path = path.substring(1);
            return path;
        } catch (Exception e) {
            return fileUrl;
        }
    }
}
