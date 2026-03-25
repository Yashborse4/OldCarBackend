package com.carselling.oldcar.service.media;

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

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaFinalizationService {

    private final B2Client b2Client;
    private final B2Properties properties;
    private final TemporaryFileRepository temporaryFileRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
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
            org.springframework.transaction.support.TransactionTemplate tt = new org.springframework.transaction.support.TransactionTemplate(
                    transactionManager);
            tt.setReadOnly(true);
            TemporaryFile tempFile = tt.execute(status -> {
                TemporaryFile file = temporaryFileRepository.findById(tempId).orElse(null);
                if (file != null && file.getUploadedBy() != null) {
                    file.getUploadedBy().getId(); // Initialize lazy proxy safely
                }
                return file;
            });

            if (tempFile == null) {
                log.warn("Temporary file not found with ID: {} - SKIPPING", tempId);
                failedFiles.add("ID " + tempId + ": Not found in database");
                continue;
            }
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

                // Ensure destination prefix exists (B2 folders are virtual, this verifies path
                // is writable)
                int lastSlash = targetPath.lastIndexOf('/');
                if (lastSlash > 0) {
                    String targetPrefix = targetPath.substring(0, lastSlash + 1);
                    b2Client.ensureFolderExists(targetPrefix);
                }

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

                        try {
                            log.info("Waiting 4 seconds for stabilization post-transfer..");
                            Thread.sleep(4000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }

                    } catch (com.backblaze.b2.client.exceptions.B2Exception b2Exception) {
                        // Source missing (404) — file may have already been moved in a prior attempt
                        if (b2Exception.getStatus() == 404 || (b2Exception.getMessage() != null
                                && b2Exception.getMessage().contains("not_found"))) {
                            log.warn("Source not found (404): {}. Checking if target exists...", tempFile.getFileId());
                            if (b2Client.fileExists(targetPath)) {
                                log.info("Target exists — treating as recovered success.");
                                alreadyExists = true;
                            } else {
                                throw b2Exception; // Real failure: neither source nor target exist
                            }
                        } else if (b2Exception.getStatus() == 429 || b2Exception.getStatus() >= 500) {
                            log.warn("Transient B2 error ({}): {}. Retrying via @Retryable...",
                                    b2Exception.getStatus(), b2Exception.getMessage());
                            throw b2Exception;
                        } else {
                            throw b2Exception;
                        }
                    } catch (Exception copyException) {
                        // B2Client.copyFile() wraps non-B2 errors in RuntimeException.
                        // Unwrap the cause to check if a B2Exception is hiding underneath.
                        Throwable rootCause = copyException.getCause();
                        if (rootCause instanceof com.backblaze.b2.client.exceptions.B2Exception b2Root) {
                            // Recovered a B2Exception from the wrapper — route through status-based logic
                            if (b2Root.getStatus() == 404 || (b2Root.getMessage() != null
                                    && b2Root.getMessage().contains("not_found"))) {
                                log.warn("Source not found (404, unwrapped): {}. Checking if target exists...",
                                        tempFile.getFileId());
                                if (b2Client.fileExists(targetPath)) {
                                    log.info("Target exists — treating as recovered success.");
                                    alreadyExists = true;
                                } else {
                                    throw copyException; // Real failure: neither source nor target exist
                                }
                            } else if (b2Root.getStatus() == 429 || b2Root.getStatus() >= 500) {
                                // Transient error — rethrow to trigger @Retryable
                                log.warn("Transient B2 error (unwrapped, {}): {}. Retrying via @Retryable...",
                                        b2Root.getStatus(), b2Root.getMessage());
                                throw copyException;
                            } else if (b2Root.getStatus() == 401 || b2Root.getStatus() == 403) {
                                // Auth/permission error — retrying won't help
                                log.error("B2 auth/permission error ({}): {}. File: {}",
                                        b2Root.getStatus(), b2Root.getMessage(), tempFile.getFileId());
                                throw copyException;
                            } else {
                                log.error("Unhandled B2 error (unwrapped, status {}): {}",
                                        b2Root.getStatus(), b2Root.getMessage());
                                throw copyException;
                            }
                        } else {
                            // Genuine non-B2 exception (IO error, OOM, etc.) — log root cause and rethrow
                            log.error("Non-B2 copy failure for file {} [{}]: {}",
                                    tempFile.getFileId(), copyException.getClass().getSimpleName(),
                                    copyException.getMessage());
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
                // Persistent failure after retries
                log.error("ALERT: FAILED to finalize file {}: {} (Exception: {})",
                        tempId, e.getMessage(), e.getClass().getSimpleName(), e);
                // Mark as FAILED with error details for per-file retry
                tempFile.setStorageStatus(StorageStatus.FAILED);
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.length() > 500) {
                    errorMsg = errorMsg.substring(0, 497) + "...";
                }
                // Record the specific exception type for better debugging
                tempFile.setLastError(String.format("[%s] %s", e.getClass().getSimpleName(), errorMsg));
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
                || nameToCheck.endsWith(".mp4") || nameToCheck.endsWith(".mov")
                || nameToCheck.endsWith(".m4a") || nameToCheck.endsWith(".mp3")
                || nameToCheck.endsWith(".wav") || nameToCheck.endsWith(".ogg")
                || nameToCheck.endsWith(".aac");

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
        if (tempFile.getContentType() != null) {
            if (tempFile.getContentType().startsWith("video/")) {
                subType = "videos";
            } else if (tempFile.getContentType().startsWith("audio/")) {
                subType = "audio";
            } else if (!tempFile.getContentType().startsWith("image/")) {
                subType = "files";
            }
        }

        // Ensure baseFolder doesn't end with slash
        String cleanBase = baseFolder.endsWith("/") ? baseFolder.substring(0, baseFolder.length() - 1) : baseFolder;

        if (cleanBase.startsWith("chat/")) {
            // For Chat Structure: chat/{chatId}/{year}/{month}/{subType}/{fileName}
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            String year = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy"));
            String month = now.format(java.time.format.DateTimeFormatter.ofPattern("MM"));

            return cleanBase + "/" + year + "/" + month + "/" + subType + "/" + tempFile.getFileName();
        }

        return cleanBase + "/" + subType + "/" + tempFile.getFileName();
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
        // Construct prefix: domain + "/temp/" + carId
        String domain = properties.getCdnDomain();
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        String tempPrefix = domain + "/temp/" + carId + "/";
        temporaryFileRepository.deleteByFileUrlStartingWith(tempPrefix);

        // 3. Clean B2 Folders (Physical files)
        try {
            // Permanent folder
            b2Client.deleteFilesWithPrefix("cars/" + carId + "/");
            // Temp folder (matches upload path: temp/{carId}/)
            b2Client.deleteFilesWithPrefix("temp/" + carId + "/");
        } catch (Exception e) {
            log.error("Failed to cleanup B2 folders for car {}: {}", carId, e.getMessage());
            // Consume error so DB transaction can commit
        }
    }
}
