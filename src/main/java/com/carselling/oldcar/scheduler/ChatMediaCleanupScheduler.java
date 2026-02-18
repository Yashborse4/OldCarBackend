package com.carselling.oldcar.scheduler;

import com.carselling.oldcar.b2.B2Client;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.model.StorageStatus;
import com.carselling.oldcar.model.UploadedFile;
import com.carselling.oldcar.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduler for cleaning up expired chat media files.
 * Retention Policy: 3 Months.
 * Files older than 3 months will be deleted from B2 storage and the database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMediaCleanupScheduler {

    private final UploadedFileRepository uploadedFileRepository;
    private final com.carselling.oldcar.repository.ChatMessageRepository chatMessageRepository;
    private final B2Client b2Client;

    /**
     * Run daily at 3:30 AM IST.
     * Deletes chat media older than 3 months.
     */
    @Scheduled(cron = "0 30 3 * * ?", zone = "Asia/Kolkata")
    @Transactional
    public void cleanupExpiredChatMedia() {
        log.info("Starting cleanup of expired chat media (Retention: 3 months)...");

        // Retention Period: 3 months
        LocalDateTime threshold = LocalDateTime.now().minusMonths(3);

        // Find expired chat files that are not already deleted
        List<UploadedFile> expiredFiles = uploadedFileRepository.findByOwnerTypeAndCreatedAtBeforeAndStorageStatusNot(
                ResourceType.CHAT_ATTACHMENT,
                threshold,
                StorageStatus.DELETED);

        if (expiredFiles.isEmpty()) {
            log.info("No expired chat media found.");
            return;
        }

        log.info("Found {} expired chat media files to delete.", expiredFiles.size());

        for (UploadedFile file : expiredFiles) {
            try {
                // 1. Update Linked Chat Message (if any)
                // We do this BEFORE deleting the file record to ensure data consistency
                chatMessageRepository.findByFileUrl(file.getFileUrl()).ifPresent(message -> {
                    log.info("Marking message ID {} as expired due to media cleanup", message.getId());
                    message.setFileUrl(null);
                    message.setFileName(null);
                    message.setFileSize(null);
                    message.setContent("Media expired");
                    message.setMessageType(com.carselling.oldcar.model.ChatMessage.MessageType.TEXT);
                    chatMessageRepository.save(message);
                });

                // 2. Delete from B2 Storage
                if (file.getFileId() != null && !file.getFileId().isEmpty()) {
                    log.debug("Deleting file from B2 by ID: {}", file.getFileId());
                    try {
                        String b2Key = extractB2Key(file);
                        b2Client.deleteFileVersion(b2Key, file.getFileId());
                    } catch (Exception e) {
                        log.warn("Failed to delete B2 file version: {}. Error: {}", file.getFileId(), e.getMessage());
                    }
                } else {
                    // Fallback to URL deletion
                    log.debug("Deleting file from B2 by URL: {}", file.getFileUrl());
                    try {
                        b2Client.deleteFileByUrl(file.getFileUrl());
                    } catch (Exception e) {
                        log.warn("Failed to delete B2 file by URL: {}. Error: {}", file.getFileUrl(), e.getMessage());
                    }
                }

                // 3. Delete from Database (Hard Delete)
                uploadedFileRepository.delete(file);

                log.debug("Deleted expired chat file record: {}", file.getId());

            } catch (Exception e) {
                log.error("Failed to cleanup chat file {}: {}", file.getId(), e.getMessage());
                // Retain in DB so it's picked up next run
            }
        }

        log.info("Chat media cleanup completed.");
    }

    private String extractB2Key(UploadedFile file) {
        return file.getFileName();
    }
}
