package com.carselling.oldcar.service;

import com.carselling.oldcar.b2.B2Client;
import com.carselling.oldcar.b2.B2Properties;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.model.StorageStatus;
import com.carselling.oldcar.model.TemporaryFile;
import com.carselling.oldcar.model.UploadedFile;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.TemporaryFileRepository;
import com.carselling.oldcar.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    @org.springframework.retry.annotation.Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 1000))
    public List<UploadedFile> finalizeUploads(List<Long> tempFileIds, String targetFolder, ResourceType ownerType,
            Long ownerId, User currentUser) {
        log.info("========== FINALIZE UPLOADS START ==========");
        log.info("Finalizing {} temporary files for owner {} (type: {}) to folder: {}",
                tempFileIds.size(), ownerId, ownerType, targetFolder);

        // Ensure storage directory is ready (Simulated creation/check)
        b2Client.ensureFolderExists(targetFolder + "/");

        log.info("TempFileIds: {}", tempFileIds);
        log.info("CurrentUser: {} (ID: {})", currentUser.getUsername(), currentUser.getId());

        List<UploadedFile> finalizedFiles = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();

        for (Long tempId : tempFileIds) {
            log.debug("Processing tempFileId: {}", tempId);
            Optional<TemporaryFile> tempFileOpt = temporaryFileRepository.findById(tempId);
            if (tempFileOpt.isEmpty()) {
                log.warn("Temporary file not found with ID: {} - SKIPPING", tempId);
                failedFiles.add("ID " + tempId + ": Not found in database");
                continue;
            }

            TemporaryFile tempFile = tempFileOpt.get();
            log.debug("Found TemporaryFile: fileName={}, fileId={}, fileUrl={}, storageStatus={}",
                    tempFile.getFileName(), tempFile.getFileId(), tempFile.getFileUrl(), tempFile.getStorageStatus());

            // STRICT SECURITY: Ownership check
            if (!tempFile.getUploadedBy().getId().equals(currentUser.getId())) {
                log.error("SECURITY VIOLATION: User {} attempted to finalize file {} owned by user {}",
                        currentUser.getId(), tempId, tempFile.getUploadedBy().getId());
                throw new SecurityException("You do not own this temporary file");
            }

            // DEFENSIVE: Ensure carId is set (handling potential race conditions or legacy
            // data)
            if (ownerType == ResourceType.CAR_IMAGE && ownerId != null) {
                if (tempFile.getCarId() == null) {
                    log.warn("Fixing missing carId for temp file {}. Setting to {}", tempId, ownerId);
                    tempFile.setCarId(ownerId);
                }
            }

            // DOUBLE CHECK VALIDATION (Paranoid Check)
            // Ensure file type/size still valid according to current policies
            validateFileBeforeFinalize(tempFile);

            // Track transfer state for observability
            tempFile.setStorageStatus(StorageStatus.TRANSFERRING);
            temporaryFileRepository.save(tempFile);

            try {
                // 1. Determine correct target path (route videos to /videos folder)
                String targetPath = determineTargetPath(targetFolder, tempFile);
                log.info("Processing file: sourceFileId={} -> targetPath={}", tempFile.getFileId(), targetPath);

                String newFileId = null;
                boolean alreadyExists = false;

                // Single idempotency check: if target already exists, skip the copy
                try {
                    alreadyExists = b2Client.fileExists(targetPath);
                    if (alreadyExists) {
                        log.info("Target file already exists (idempotent skip): {}", targetPath);
                    }
                } catch (Exception e) {
                    log.warn("Failed to check existence for {}: {}", targetPath, e.getMessage());
                }

                if (!alreadyExists) {
                    try {
                        newFileId = b2Client.copyFile(tempFile.getFileId(), targetPath);
                        log.info("B2 copyFile SUCCESS for tempId: {} (newFileId: {})", tempId, newFileId);
                    } catch (Exception copyException) {
                        // Source missing (404) — file may have already been moved in a prior attempt
                        if (copyException.getMessage() != null && copyException.getMessage().contains("not_found")) {
                            log.warn("Source not found (404): {}. Checking if target exists...", tempFile.getFileId());
                            if (b2Client.fileExists(targetPath)) {
                                log.info("Target exists — treating as recovered success.");
                                alreadyExists = true;
                            } else {
                                throw copyException; // Real failure: neither source nor target exist
                            }
                        } else {
                            throw copyException;
                        }
                    }
                }

                // If file already existed (idempotent or recovered), look up its fileId
                if (alreadyExists && newFileId == null) {
                    try {
                        for (com.backblaze.b2.client.structures.B2FileVersion v : b2Client.listFiles(targetPath)) {
                            newFileId = v.getFileId();
                            break;
                        }
                    } catch (Exception e) {
                        log.warn("Could not retrieve fileId for existing file: {}", targetPath, e);
                    }
                }

                if (newFileId == null) {
                    throw new RuntimeException("Could not finalize file: failed to get file ID");
                }

                // Mark as transferred before creating permanent record
                tempFile.setStorageStatus(StorageStatus.TRANSFERRED);
                temporaryFileRepository.save(tempFile);

                // 2. Delete source file (temp) from B2
                // Use the full B2 key extracted from the CDN URL, not just the basename
                /*
                 * try {
                 * String b2TempKey = extractB2Key(tempFile.getFileUrl());
                 * b2Client.deleteFileVersion(b2TempKey, tempFile.getFileId());
                 * log.debug("Deleted temp B2 file: {}", b2TempKey);
                 * } catch (Exception e) {
                 * log.warn("Failed to delete temp B2 file {}: {}", tempFile.getFileUrl(),
                 * e.getMessage());
                 * // Non-critical: file will be cleaned up by expiry job
                 * }
                 */

                // 3. Build permanent URL from CDN domain + target path
                String domain = properties.getCdnDomain();
                if (domain.endsWith("/")) {
                    domain = domain.substring(0, domain.length() - 1);
                }
                String newUrl = domain + "/" + targetPath;
                log.debug("New permanent URL: {}", newUrl);

                // 4. Create UploadedFile record with new fileId
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
                        .fileId(newFileId)
                        .build();

                uploadedFileRepository.save(uploadedFile);
                finalizedFiles.add(uploadedFile);
                log.info("Saved UploadedFile: id={}, url={}, fileId={}",
                        uploadedFile.getId(), uploadedFile.getFileUrl(), uploadedFile.getFileId());

                // 5. Delete Temp Record from DB
                // temporaryFileRepository.delete(tempFile);
                log.info("Retained TemporaryFile record (TRANSFERRED): {}", tempId);

            } catch (Exception e) {
                log.error("FAILED to finalize file {}: {} (Exception: {})",
                        tempId, e.getMessage(), e.getClass().getSimpleName(), e);
                // Mark as FAILED with error details for per-file retry
                tempFile.setStorageStatus(StorageStatus.FAILED);
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.length() > 500) {
                    errorMsg = errorMsg.substring(0, 497) + "...";
                }
                tempFile.setLastError(errorMsg);
                int retryCount = (tempFile.getRetryCount() != null ? tempFile.getRetryCount() : 0) + 1;
                tempFile.setRetryCount(retryCount);
                // Exponential backoff: 1m, 2m, 4m
                long delaySec = 60L * (1L << Math.min(retryCount - 1, 3));
                tempFile.setNextRetryAt(LocalDateTime.now().plusSeconds(delaySec));
                temporaryFileRepository.save(tempFile);
                failedFiles.add("ID " + tempId + ": " + e.getMessage());
            }
        }

        log.info("========== FINALIZE UPLOADS COMPLETE ==========");
        log.info("Result: {} finalized, {} failed", finalizedFiles.size(), failedFiles.size());

        // If any files failed and we finalized nothing, throw an error
        if (finalizedFiles.isEmpty() && !tempFileIds.isEmpty()) {
            String errorMsg = "All files failed to finalize: " + String.join("; ", failedFiles);
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        // If some files failed but others succeeded, log warning
        if (!failedFiles.isEmpty()) {
            log.warn("Partial finalization failure. Failed files: {}", failedFiles);
        }

        return finalizedFiles;
    }

    private void validateFileBeforeFinalize(TemporaryFile tempFile) {
        // Use the original human-readable file name for extension validation,
        // not the UUID-based fileName generated during upload
        String nameToCheck = (tempFile.getOriginalFileName() != null
                ? tempFile.getOriginalFileName()
                : tempFile.getFileName()).toLowerCase();

        boolean validExt = nameToCheck.endsWith(".jpg") || nameToCheck.endsWith(".jpeg")
                || nameToCheck.endsWith(".png") || nameToCheck.endsWith(".webp")
                || nameToCheck.endsWith(".mp4") || nameToCheck.endsWith(".mov");

        if (!validExt) {
            throw new SecurityException("File extension not allowed for finalization: " + nameToCheck);
        }

        // Size validation: 500MB hard limit for finalization safety
        if (tempFile.getFileSize() > 400 * 1024 * 1024) {
            throw new SecurityException("File too large for finalization processing");
        }
    }

    /**
     * Determine the correct B2 target path.
     * Routes video files to the /videos sub-folder instead of /images.
     */
    private String determineTargetPath(String baseFolder, TemporaryFile tempFile) {
        String subType = "images"; // Default to images
        if (tempFile.getContentType() != null && tempFile.getContentType().startsWith("video/")) {
            subType = "videos";
        }

        // Ensure baseFolder doesn't end with slash
        String cleanBase = baseFolder.endsWith("/") ? baseFolder.substring(0, baseFolder.length() - 1) : baseFolder;

        return cleanBase + "/" + subType + "/" + tempFile.getFileName();
    }

    /**
     * Extract the B2 object key from a full CDN URL.
     * e.g. "https://cdn.example.com/temp/cars/123/images/file.jpg" →
     * "temp/cars/123/images/file.jpg"
     */
    private String extractB2Key(String fileUrl) {
        String domain = properties.getCdnDomain();
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        String key = fileUrl.replace(domain + "/", "");
        if (key.startsWith("/")) {
            key = key.substring(1);
        }
        return key;
    }

    /**
     * Completely cleans up all media associated with a car.
     * Including permanent files, temporary files, and database records.
     */
    @Transactional
    public void cleanupCarMedia(Long carId) {
        log.info("Starting complete media cleanup for car: {}", carId);

        // 1. Delete UploadedFile records (Permanent DB records)
        uploadedFileRepository.deleteByOwnerTypeAndOwnerId(ResourceType.CAR_IMAGE, carId);

        // 2. Delete TemporaryFile records (Temp DB records)
        // Construct prefix: domain + "/temp/cars/" + carId
        String domain = properties.getCdnDomain();
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        String tempPrefix = domain + "/temp/cars/" + carId + "/";
        temporaryFileRepository.deleteByFileUrlStartingWith(tempPrefix);

        // 3. Clean B2 Folders (Physical files)
        try {
            // Permanent folder
            b2Client.deleteFilesWithPrefix("cars/" + carId + "/");
            // Temp folder
            b2Client.deleteFilesWithPrefix("temp/cars/" + carId + "/");
        } catch (Exception e) {
            log.error("Failed to cleanup B2 folders for car {}: {}", carId, e.getMessage());
            // Consume error so DB transaction can commit
        }
    }
}
