package com.carselling.oldcar.service.job;

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
 * Files that are uploaded but never finalized (e.g. form abandoned) need to be
 * deleted
 * from both the database and B2 storage to save costs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileCleanupService {

    private final TemporaryFileRepository temporaryFileRepository;
    private final B2Client b2Client;

    /**
     * Run daily at 2 AM IST to clean up files older than 24 hours.
     */
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Kolkata")
    @Transactional
    public void cleanupAbandonedFiles() {
        log.info("Starting cleanup of abandoned temporary files...");

        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);

        // Find files older than 24 hours
        // Note: Repository needs a method findByCreatedAtBefore
        List<TemporaryFile> abandonedFiles = temporaryFileRepository.findByCreatedAtBefore(cutoffTime);

        if (abandonedFiles.isEmpty()) {
            log.info("No abandoned files found to clean up.");
            return;
        }

        log.info("Found {} abandoned files to clean up.", abandonedFiles.size());

        int deletedCount = 0;
        int errorCount = 0;

        for (TemporaryFile file : abandonedFiles) {
            try {
                // Delete from B2
                // We need to decode the B2 file name if it was encoded?
                // B2Client.deleteFileVersion takes (fileName, fileId)
                // TemporaryFile stores decoded logical fileName usually, but we need strictly
                // what B2 expects.
                // However, fileId is the specific version, which is robust.

                // Construct path if needed, but B2 deleteFileVersion might just need Name + ID.
                // B2Client wrapper: client.deleteFileVersion(fileName, fileId);

                // We need the full path/name as stored in B2.
                // TemporaryFile has 'fileUrl' which is public URL.
                // We should rely on fileId mostly, but SDK might require name.
                // Reconstruct name from URL or use stored name?
                // TemporaryFile.fileName might be just "foo.jpg".
                // We need "temp/123/foo.jpg".

                String b2FileName = extractB2Name(file.getFileUrl());

                b2Client.deleteFileVersion(b2FileName, file.getFileId());

                // Delete from DB
                temporaryFileRepository.delete(file);
                deletedCount++;

            } catch (Exception e) {
                log.error("Failed to clean up file ID {}: {}", file.getId(), e.getMessage());
                errorCount++;
            }
        }

        log.info("Cleanup completed. Deleted: {}, Errors: {}", deletedCount, errorCount);
    }

    private String extractB2Name(String fileUrl) {
        // Logic to extract "temp/123/file.jpg" from "https://cdn.com/temp/123/file.jpg"
        try {
            java.net.URI uri = new java.net.URI(fileUrl);
            String path = uri.getPath();
            if (path.startsWith("/"))
                path = path.substring(1);
            return path;
        } catch (Exception e) {
            return fileUrl; // Fallback
        }
    }
}
