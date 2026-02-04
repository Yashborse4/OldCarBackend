package com.carselling.oldcar.service;

import com.carselling.oldcar.b2.B2Client;
import com.carselling.oldcar.b2.B2Properties;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.model.TemporaryFile;
import com.carselling.oldcar.model.UploadedFile;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.TemporaryFileRepository;
import com.carselling.oldcar.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaFinalizationService {

    private final B2Client b2Client;
    private final B2Properties properties;
    private final TemporaryFileRepository temporaryFileRepository;
    private final UploadedFileRepository uploadedFileRepository;

    @Transactional
    public List<UploadedFile> finalizeUploads(List<Long> tempFileIds, String targetFolder, ResourceType ownerType,
            Long ownerId, User currentUser) {
        List<UploadedFile> finalizedFiles = new ArrayList<>();

        for (Long tempId : tempFileIds) {
            Optional<TemporaryFile> tempFileOpt = temporaryFileRepository.findById(tempId);
            if (tempFileOpt.isEmpty()) {
                log.warn("Temporary file not found with ID: {}", tempId);
                continue;
            }

            TemporaryFile tempFile = tempFileOpt.get();

            // STRICT SECURITY: Ownership check
            if (!tempFile.getUploadedBy().getId().equals(currentUser.getId())) {
                log.warn("User {} attempted to finalize file {} owned by user {}", currentUser.getId(), tempId,
                        tempFile.getUploadedBy().getId());
                throw new SecurityException("You do not own this temporary file");
            }

            // DOUBLE CHECK VALIDATION (Paranoid Check)
            // Ensure file type/size still valid according to current policies
            // (e.g. if config changed since upload)
            validateFileBeforeFinalize(tempFile);

            try {
                // 1. Move file in B2 (Copy to targetFolder + Delete from temp)
                String targetFileName = targetFolder + "/" + tempFile.getFileName();

                // Use stored fileId for copy
                b2Client.copyFile(tempFile.getFileId(), targetFileName);

                // Delete source file (temp)
                // DISABLED as per user requirement: Keep temp file for 24h as backup.
                // TempFileCleanupService will handle deletion.
                // b2Client.deleteFileVersion(sourceFileName, tempFile.getFileId());

                // Update URL
                // Construct new URL using configured CDN domain
                String domain = properties.getCdnDomain();
                if (domain.endsWith("/")) {
                    domain = domain.substring(0, domain.length() - 1);
                }

                // Assuming targetFileName is safe (e.g. "cars/123/images/file.jpg")
                // And simple concatenation works for Cloudflare/B2 mapping.
                String newUrl = domain + "/" + targetFileName;

                UploadedFile uploadedFile = UploadedFile.builder()
                        .fileName(tempFile.getFileName())
                        .originalFileName(tempFile.getOriginalFileName())
                        .contentType(tempFile.getContentType())
                        .size(tempFile.getFileSize())
                        .uploadedBy(currentUser)
                        .ownerType(ownerType)
                        .ownerId(ownerId)
                        .fileHash(tempFile.getFileHash())
                        .fileUrl(newUrl)
                        .build();

                uploadedFileRepository.save(uploadedFile);
                finalizedFiles.add(uploadedFile);

                // Delete temp record
                temporaryFileRepository.delete(tempFile);

            } catch (Exception e) {
                log.error("Failed to finalize file {}: {}", tempId, e.getMessage());
                // Don't fail entire batch?
            }
        }

        return finalizedFiles;
    }

    private void validateFileBeforeFinalize(TemporaryFile tempFile) {
        // Example: Check if Extension is still allowed
        // (Simplified check, assuming extensions are generally static in config, but
        // good for robust audit)
        String fileName = tempFile.getFileName().toLowerCase();
        boolean validExt = fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") ||
                fileName.endsWith(".webp") || fileName.endsWith(".mp4") || fileName.endsWith(".mov");

        if (!validExt) {
            throw new SecurityException("File extension not allowed for finalization: " + tempFile.getFileName());
        }

        // Size validation could also be re-run here if policy became stricter
        if (tempFile.getFileSize() > 200 * 1024 * 1024) { // 200MB hard limit for finalization safety
            throw new SecurityException("File too large for finalization processing");
        }
    }
}
