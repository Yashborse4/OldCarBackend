package com.carselling.oldcar.service;

import com.carselling.oldcar.model.UploadedFile;
import com.carselling.oldcar.model.StorageStatus;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TempFileStorageService {

    @Value("${file.upload.temp-directory:/tmp/car-uploads}")
    private String tempDirectory;

    @Value("${file.upload.temp-expiry-hours:24}")
    private int tempExpiryHours;

    private final UploadedFileRepository uploadedFileRepository;
    private final FileValidationService fileValidationService;

    @Transactional
    public UploadedFile storeTempFile(MultipartFile file, User uploadedBy, ResourceType ownerType, Long ownerId)
            throws IOException {
        log.info("Storing temp file: {} for user: {} and owner: {}",
                file.getOriginalFilename(), uploadedBy.getId(), ownerId);

        // Create temp directory if it doesn't exist
        Path tempDirPath = Paths.get(tempDirectory);
        if (!Files.exists(tempDirPath)) {
            Files.createDirectories(tempDirPath);
            log.info("Created temp directory: {}", tempDirectory);
        }

        // Generate unique file name
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        String uniqueFileName = UUID.randomUUID().toString() + "." + fileExtension;
        Path tempFilePath = tempDirPath.resolve(uniqueFileName);

        // Save file to temp location
        file.transferTo(tempFilePath.toFile());
        log.info("File saved to temp location: {}", tempFilePath);

        // Create UploadedFile record
        UploadedFile uploadedFile = UploadedFile.builder()
                .fileName(uniqueFileName)
                .originalFileName(originalFilename)
                .fileUrl(tempFilePath.toString())
                .contentType(file.getContentType())
                .size(file.getSize())
                .uploadedBy(uploadedBy)
                .ownerType(ownerType)
                .ownerId(ownerId)
                .storageStatus(StorageStatus.TEMPORARY)
                .tempExpiresAt(LocalDateTime.now().plusHours(tempExpiryHours))
                .validationStatus(fileValidationService.validateFile(file, null)
                        ? com.carselling.oldcar.model.ValidationStatus.VALIDATED
                        : com.carselling.oldcar.model.ValidationStatus.PENDING)
                .build();

        // Validate the file
        boolean isValid = fileValidationService.validateFile(file, uploadedFile);
        if (!isValid) {
            // Clean up the temp file if validation failed
            Files.deleteIfExists(tempFilePath);
            log.warn("Temp file validation failed, cleaned up: {}", originalFilename);
            throw new IllegalArgumentException("File validation failed: " + uploadedFile.getValidationErrors());
        }

        uploadedFile = uploadedFileRepository.save(uploadedFile);
        log.info("Temp file record created with ID: {}", uploadedFile.getId());

        return uploadedFile;
    }

    @Transactional
    public boolean transferToPermanent(UploadedFile tempFile, String permanentFileUrl, String permanentFileId) {
        log.info("Transferring temp file {} to permanent storage", tempFile.getId());

        if (tempFile.getStorageStatus() != StorageStatus.TEMPORARY) {
            log.error("File {} is not in temp status, cannot transfer", tempFile.getId());
            return false;
        }

        try {
            // Update file status to transferring
            tempFile.setStorageStatus(StorageStatus.TRANSFERRING);
            uploadedFileRepository.save(tempFile);

            // Delete temp file from filesystem
            Path tempFilePath = Paths.get(tempFile.getFileUrl());
            boolean deleted = Files.deleteIfExists(tempFilePath);
            if (deleted) {
                log.info("Deleted temp file: {}", tempFilePath);
            }

            // Update file record with permanent location
            tempFile.setFileUrl(permanentFileUrl);
            tempFile.setFileId(permanentFileId);
            tempFile.setStorageStatus(StorageStatus.TRANSFERRED);
            tempFile.setTempExpiresAt(null);
            uploadedFileRepository.save(tempFile);

            log.info("Successfully transferred temp file {} to permanent storage", tempFile.getId());
            return true;

        } catch (IOException e) {
            log.error("IO Error transferring temp file {} to permanent storage", tempFile.getId(), e);
            tempFile.setStorageStatus(StorageStatus.FAILED);
            tempFile.setValidationErrors("Transfer failed (IO): " + e.getMessage());
            uploadedFileRepository.save(tempFile);
            return false;
        } catch (Exception e) {
            log.error("Unexpected error transferring temp file {} to permanent storage", tempFile.getId(), e);
            tempFile.setStorageStatus(StorageStatus.FAILED);
            tempFile.setValidationErrors("Transfer failed (Unexpected): " + e.getMessage());
            uploadedFileRepository.save(tempFile);
            return false;
        }
    }

    @Transactional
    public void cleanupExpiredTempFiles() {
        log.info("Starting cleanup of expired temp files");

        LocalDateTime now = LocalDateTime.now();
        List<UploadedFile> expiredFiles = uploadedFileRepository.findByStorageStatusAndTempExpiresAtBefore(
                StorageStatus.TEMPORARY, now);

        log.info("Found {} expired temp files to clean up", expiredFiles.size());

        for (UploadedFile file : expiredFiles) {
            try {
                // Delete temp file from filesystem
                Path tempFilePath = Paths.get(file.getFileUrl());
                boolean deleted = Files.deleteIfExists(tempFilePath);
                if (deleted) {
                    log.info("Deleted expired temp file: {}", tempFilePath);
                }

                // Delete database record
                uploadedFileRepository.delete(file);
                log.info("Deleted expired temp file record: {}", file.getId());

            } catch (IOException e) {
                log.error("IO Error cleaning up expired temp file: {}", file.getId(), e);
            } catch (Exception e) {
                log.error("Unexpected error cleaning up expired temp file: {}", file.getId(), e);
            }
        }

        log.info("Completed cleanup of expired temp files");
    }

    public List<UploadedFile> getTempFilesForOwner(ResourceType ownerType, Long ownerId) {
        return uploadedFileRepository.findByOwnerTypeAndOwnerIdAndStorageStatus(
                ownerType, ownerId, StorageStatus.TEMPORARY);
    }

    public boolean isTempFileExpired(UploadedFile file) {
        if (file.getStorageStatus() != StorageStatus.TEMPORARY || file.getTempExpiresAt() == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(file.getTempExpiresAt());
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        return (lastDotIndex > 0 && lastDotIndex < filename.length() - 1)
                ? filename.substring(lastDotIndex + 1).toLowerCase()
                : "";
    }
}