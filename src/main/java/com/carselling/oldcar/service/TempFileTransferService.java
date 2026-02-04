package com.carselling.oldcar.service;

import com.carselling.oldcar.model.UploadedFile;
import com.carselling.oldcar.model.StorageStatus;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.scheduling.annotation.Scheduled;

@Service
@RequiredArgsConstructor
@Slf4j
public class TempFileTransferService {

    @Value("${file.upload.temp-directory:/tmp/car-uploads}")
    private String tempDirectory;

    @Value("${file.upload.permanent-directory:/app/uploads}")
    private String permanentDirectory;

    private final UploadedFileRepository uploadedFileRepository;
    private final FileValidationService fileValidationService;

    @Transactional
    public List<UploadedFile> transferTempFilesToPermanent(List<Long> tempFileIds, ResourceType ownerType, Long ownerId,
            User currentUser) {
        List<UploadedFile> transferredFiles = new ArrayList<>();

        for (Long tempFileId : tempFileIds) {
            Optional<UploadedFile> tempFileOpt = uploadedFileRepository.findById(tempFileId);
            if (tempFileOpt.isEmpty()) {
                log.warn("Temp file not found with ID: {}", tempFileId);
                continue;
            }

            UploadedFile tempFile = tempFileOpt.get();

            // Security check: Ensure user owns the temp file
            if (tempFile.getUploadedBy().getId() != null
                    && !tempFile.getUploadedBy().getId().equals(currentUser.getId())) {
                log.warn("User {} attempted to transfer temp file {} owned by user {}",
                        currentUser.getId(), tempFileId, tempFile.getUploadedBy().getId());
                throw new SecurityException("You do not own this temporary file");
            }

            // Check if file is still temporary
            if (tempFile.getStorageStatus() != StorageStatus.TEMPORARY) {
                log.warn("File {} is not in temporary status, cannot transfer", tempFileId);
                continue;
            }

            // Check if temp file has expired
            if (tempFile.getTempExpiresAt() != null && LocalDateTime.now().isAfter(tempFile.getTempExpiresAt())) {
                log.warn("Temp file {} has expired, cannot transfer", tempFileId);
                cleanupExpiredTempFile(tempFile);
                continue;
            }

            // Re-validate the file before transfer
            if (!fileValidationService.validateFile(null, tempFile)) {
                log.warn("Temp file {} failed re-validation, cannot transfer", tempFileId);
                continue;
            }

            try {
                // Transfer the file
                UploadedFile permanentFile = transferSingleTempFile(tempFile, ownerType, ownerId);
                if (permanentFile != null) {
                    transferredFiles.add(permanentFile);
                }
            } catch (Exception e) {
                log.error("Failed to transfer temp file {}: {}", tempFileId, e.getMessage());
            }
        }

        return transferredFiles;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredTempFiles() {
        log.info("Starting scheduled cleanup of expired temp files");
        try {
            List<UploadedFile> expiredFiles = uploadedFileRepository.findExpiredTempFiles(StorageStatus.TEMPORARY,
                    LocalDateTime.now());
            log.info("Found {} expired temp files to clean up", expiredFiles.size());

            for (UploadedFile file : expiredFiles) {
                cleanupExpiredTempFile(file);
            }
        } catch (Exception e) {
            log.error("Error during scheduled temp file cleanup", e);
        }
        log.info("Completed scheduled cleanup");
    }

    private UploadedFile transferSingleTempFile(UploadedFile tempFile, ResourceType ownerType, Long ownerId) {
        try {
            log.info("Transferring temp file {} to permanent storage", tempFile.getId());

            // Update status to transferring
            tempFile.setStorageStatus(StorageStatus.TRANSFERRING);
            uploadedFileRepository.save(tempFile);

            // Create permanent directory structure
            Path permanentDirPath = Paths.get(permanentDirectory, ownerType.name().toLowerCase(),
                    String.valueOf(ownerId));
            if (!Files.exists(permanentDirPath)) {
                Files.createDirectories(permanentDirPath);
                log.info("Created permanent directory: {}", permanentDirPath);
            }

            // Generate permanent file name
            String permanentFileName = generatePermanentFileName(tempFile);
            Path permanentFilePath = permanentDirPath.resolve(permanentFileName);

            // Copy file from temp to permanent location
            Path tempFilePath = Paths.get(tempFile.getFileUrl());
            Files.copy(tempFilePath, permanentFilePath);

            // Delete temp file
            Files.deleteIfExists(tempFilePath);
            log.info("Deleted temp file: {}", tempFilePath);

            // Update file record with permanent location
            tempFile.setFileUrl(permanentFilePath.toString());
            tempFile.setOriginalFileName(permanentFileName);
            tempFile.setStorageStatus(StorageStatus.PERMANENT);
            tempFile.setTempExpiresAt(null);
            tempFile.setOwnerType(ownerType);
            tempFile.setOwnerId(ownerId);

            UploadedFile permanentFile = uploadedFileRepository.save(tempFile);
            log.info("Successfully transferred temp file {} to permanent storage at: {}",
                    tempFile.getId(), permanentFilePath);

            return permanentFile;

        } catch (Exception e) {
            log.error("Error transferring temp file {} to permanent storage", tempFile.getId(), e);
            tempFile.setStorageStatus(StorageStatus.FAILED);
            tempFile.setValidationErrors("Transfer failed: " + e.getMessage());
            uploadedFileRepository.save(tempFile);
            return null;
        }
    }

    private void cleanupExpiredTempFile(UploadedFile tempFile) {
        try {
            // Delete temp file from filesystem
            Path tempFilePath = Paths.get(tempFile.getFileUrl());
            Files.deleteIfExists(tempFilePath);
            log.info("Deleted expired temp file: {}", tempFilePath);

            // Delete database record
            uploadedFileRepository.delete(tempFile);
            log.info("Deleted expired temp file record: {}", tempFile.getId());

        } catch (Exception e) {
            log.error("Error cleaning up expired temp file: {}", tempFile.getId(), e);
        }
    }

    private String generatePermanentFileName(UploadedFile tempFile) {
        // Generate a unique name based on timestamp and original name
        String timestamp = String.valueOf(System.currentTimeMillis());
        String originalName = tempFile.getOriginalFileName();
        String extension = "";

        int lastDotIndex = originalName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < originalName.length() - 1) {
            extension = originalName.substring(lastDotIndex);
        }

        return "file_" + timestamp + "_" + tempFile.getId() + extension;
    }
}