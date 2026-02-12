package com.carselling.oldcar.scheduler;

import com.carselling.oldcar.b2.B2Client;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarStatus;
import com.carselling.oldcar.model.MediaStatus;
import com.carselling.oldcar.model.StorageStatus;
import com.carselling.oldcar.model.TemporaryFile;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.TemporaryFileRepository;
import com.carselling.oldcar.service.car.CarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scheduled job for media finalization recovery and cleanup.
 *
 * Responsibilities:
 * 1. Retry car-level finalization for stuck PROCESSING cars (per-car retry)
 * 2. Retry individual FAILED temp files (per-file retry)
 * 3. Cleanup stale temp files that were never finalized (garbage collection)
 *
 * Retry Strategy:
 * - Exponential backoff: 1m, 2m, 4m, 8m, 16m
 * - Max 5 retries per car, then mark as FAILED
 * - Per-file retries use the same backoff on TemporaryFile.nextRetryAt
 * - Stale cleanup runs daily at 3 AM for files older than 48h
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MediaFinalizationStartupRunner implements CommandLineRunner {

    private final CarRepository carRepository;
    private final TemporaryFileRepository temporaryFileRepository;
    private final CarService carService;
    private final B2Client b2Client;

    // Retry configuration
    private static final int MAX_CAR_RETRIES = 5;
    private static final int MAX_FILE_RETRIES = 3;
    private static final long INITIAL_BACKOFF_SECONDS = 60; // 1 minute
    private static final long STALE_THRESHOLD_HOURS = 48;

    @Override
    public void run(String... args) throws Exception {
        log.info("Startup: Triggering initial media finalization check...");
        processCarRetries();
    }

    /**
     * Periodic car-level retry job.
     * Indexes used: idx_car_retry (status, next_retry_at)
     * Runs every minute to pick up due car-level retries.
     */
    @Scheduled(fixedDelay = 60_000)
    public void scheduleCarProcessing() {
        processCarRetries();
    }

    /**
     * Periodic per-file retry job.
     * Picks up individual FAILED temp files that are due for retry.
     * Runs every 2 minutes, offset from car-level retries.
     */
    @Scheduled(fixedDelay = 120_000, initialDelay = 30_000)
    public void scheduleFileRetries() {
        processFailedFileRetries();
    }

    /**
     * Daily stale temp file cleanup.
     * Removes temp files older than 48h that were never finalized.
     * Runs at 3 AM server time.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupStaleTempFiles() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(STALE_THRESHOLD_HOURS);
        List<TemporaryFile> staleFiles = temporaryFileRepository
                .findByStorageStatusAndCreatedAtBefore(StorageStatus.TEMPORARY, threshold);

        if (staleFiles.isEmpty()) {
            return;
        }

        log.info("Cleaning up {} stale temp files older than {}h", staleFiles.size(), STALE_THRESHOLD_HOURS);

        int cleaned = 0;
        for (TemporaryFile file : staleFiles) {
            try {
                // Delete from B2 storage
                b2Client.deleteFileVersion(file.getFileName(), file.getFileId());
                // Delete DB record
                temporaryFileRepository.delete(file);
                cleaned++;
                log.debug("Cleaned stale temp file: id={}, carId={}, age={}h",
                        file.getId(), file.getCarId(),
                        java.time.Duration.between(file.getCreatedAt(), LocalDateTime.now()).toHours());
            } catch (Exception e) {
                log.warn("Failed to cleanup stale temp file {}: {}", file.getId(), e.getMessage());
            }
        }
        log.info("Stale cleanup complete: {}/{} files removed", cleaned, staleFiles.size());
    }

    // ========================================================================
    // Car-Level Retry Logic
    // ========================================================================

    @Transactional
    public void processCarRetries() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Process cars due for retry (scheduled retries)
        List<Car> dueCars = carRepository.findTop10ByStatusAndMediaStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
                CarStatus.PROCESSING, MediaStatus.INIT, now);

        if (!dueCars.isEmpty()) {
            log.info("Found {} cars due for media finalization retry.", dueCars.size());
            dueCars.forEach(this::processCar);
        }

        // 2. Rescue orphaned tasks — cars stuck in PROCESSING with no retry schedule
        handleStuckCarsWithNullRetry();
    }

    private void handleStuckCarsWithNullRetry() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<Car> processingCars = carRepository.findByStatusAndMediaStatusAndCreatedAtAfter(
                CarStatus.PROCESSING, MediaStatus.INIT, threshold);

        for (Car car : processingCars) {
            if (car.getNextRetryAt() == null) {
                log.info("Initializing retry schedule for stuck car {}", car.getId());
                car.setNextRetryAt(LocalDateTime.now());
                car.setRetryCount(0);
                carRepository.save(car);
                processCar(car);
            }
        }
    }

    private void processCar(Car car) {
        try {
            log.info("Attempting media finalization for Car ID: {} (Retry {}/{})",
                    car.getId(), car.getRetryCount(), MAX_CAR_RETRIES);

            List<TemporaryFile> tempFiles = temporaryFileRepository.findByCarIdAndStorageStatus(
                    car.getId(), StorageStatus.TEMPORARY);

            if (tempFiles.isEmpty()) {
                // Also check for FAILED files that might need re-processing
                List<TemporaryFile> failedFiles = temporaryFileRepository.findByCarIdAndStorageStatus(
                        car.getId(), StorageStatus.FAILED);

                if (failedFiles.isEmpty()) {
                    log.warn("Car {} has no temp or failed files. Marking as FAILED.", car.getId());
                    failCar(car);
                    return;
                }

                // Reset FAILED files to TEMPORARY for re-processing
                for (TemporaryFile f : failedFiles) {
                    f.setStorageStatus(StorageStatus.TEMPORARY);
                    temporaryFileRepository.save(f);
                }
                tempFiles = failedFiles;
            }

            List<Long> tempFileIds = tempFiles.stream()
                    .map(TemporaryFile::getId)
                    .collect(Collectors.toList());

            carService.finalizeMedia(String.valueOf(car.getId()), tempFileIds, null);

        } catch (Exception e) {
            handleCarFailure(car, e);
        }
    }

    private void handleCarFailure(Car car, Exception e) {
        int newRetryCount = car.getRetryCount() + 1;
        car.setRetryCount(newRetryCount);

        if (newRetryCount > MAX_CAR_RETRIES) {
            log.error("Max retries ({}) reached for Car {}. Marking as FAILED. Last error: {}",
                    MAX_CAR_RETRIES, car.getId(), e.getMessage());
            failCar(car);
        } else {
            // Exponential backoff: 1m, 2m, 4m, 8m, 16m
            long delay = INITIAL_BACKOFF_SECONDS * (1L << (newRetryCount - 1));
            LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(delay);
            car.setNextRetryAt(nextRetry);
            log.warn("Finalization failed for Car {}. Rescheduling retry #{} at {}. Error: {}",
                    car.getId(), newRetryCount, nextRetry, e.getMessage());
            carRepository.save(car);
        }
    }

    private void failCar(Car car) {
        car.setStatus(CarStatus.DRAFT);
        car.setMediaStatus(MediaStatus.FAILED);
        car.setNextRetryAt(null); // Stop retrying
        carRepository.save(car);
    }

    // ========================================================================
    // Per-File Retry Logic
    // ========================================================================

    @Transactional
    public void processFailedFileRetries() {
        LocalDateTime now = LocalDateTime.now();

        List<TemporaryFile> failedFiles = temporaryFileRepository
                .findTop20ByStorageStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(StorageStatus.FAILED, now);

        if (failedFiles.isEmpty()) {
            return;
        }

        log.info("Found {} FAILED temp files due for retry.", failedFiles.size());

        for (TemporaryFile file : failedFiles) {
            if (file.getRetryCount() != null && file.getRetryCount() >= MAX_FILE_RETRIES) {
                log.error("Max file retries ({}) reached for temp file {}. Skipping. Last error: {}",
                        MAX_FILE_RETRIES, file.getId(), file.getLastError());
                // Leave as FAILED — car-level retry or manual intervention needed
                file.setNextRetryAt(null);
                temporaryFileRepository.save(file);
                continue;
            }

            // Reset to TEMPORARY so the next car-level finalization picks it up
            file.setStorageStatus(StorageStatus.TEMPORARY);
            int newRetryCount = (file.getRetryCount() != null ? file.getRetryCount() : 0) + 1;
            file.setRetryCount(newRetryCount);
            temporaryFileRepository.save(file);

            log.info("Reset FAILED temp file {} to TEMPORARY for retry #{} (carId: {})",
                    file.getId(), newRetryCount, file.getCarId());
        }
    }
}
