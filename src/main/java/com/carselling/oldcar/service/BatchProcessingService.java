package com.carselling.oldcar.service;

import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service for batch processing operations like bulk imports, exports, and async
 * tasks
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchProcessingService {

    private final VehicleRepository vehicleRepository;
    private final FileUploadService fileUploadService;
    private final NotificationService notificationService;

    // Track batch job status
    private final Map<String, BatchJobStatus> batchJobs = new ConcurrentHashMap<>();

    /**
     * Batch import vehicles from CSV file
     */
    @Async
    public CompletableFuture<String> importVehiclesFromCsv(MultipartFile file, User user) {
        String jobId = UUID.randomUUID().toString();
        BatchJobStatus status = new BatchJobStatus(jobId, "VEHICLE_IMPORT", "RUNNING");
        batchJobs.put(jobId, status);

        try {
            log.info("Starting vehicle batch import for user: {}", user.getEmail());

            List<VehicleImportRow> importRows = parseCsvFile(file);
            status.setTotalRecords(importRows.size());

            AtomicInteger processedCount = new AtomicInteger(0);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            List<String> errors = new ArrayList<>();

            // Process in batches of 50
            int batchSize = 50;
            for (int i = 0; i < importRows.size(); i += batchSize) {
                List<VehicleImportRow> batch = importRows.subList(i,
                        Math.min(i + batchSize, importRows.size()));

                processBatch(batch, user, processedCount, successCount, errorCount, errors);

                // Update status
                status.setProcessedRecords(processedCount.get());
                status.setSuccessCount(successCount.get());
                status.setErrorCount(errorCount.get());
                status.setErrors(errors);

                // Small delay to prevent overwhelming the system
                Thread.sleep(100);
            }

            // Complete the job
            status.setStatus("COMPLETED");
            status.setCompletedAt(LocalDateTime.now());

            // Send completion notification
            String details = String.format("Successfully imported %d out of %d vehicles. Errors: %d",
                    successCount.get(), importRows.size(), errorCount.get());
            notificationService.sendBatchJobCompletionNotification(user, "Vehicle Import", "COMPLETED", details);

            log.info("Vehicle Import Completed for user {}: {}", user.getId(), details);

            log.info("Completed vehicle batch import. Success: {}, Errors: {}",
                    successCount.get(), errorCount.get());

            return CompletableFuture.completedFuture(jobId);

        } catch (Exception e) {
            log.error("Error during batch vehicle import: {}", e.getMessage(), e);
            status.setStatus("FAILED");
            status.setErrorMessage(e.getMessage());
            status.setCompletedAt(LocalDateTime.now());

            notificationService.sendBatchJobCompletionNotification(user, "Vehicle Import", "FAILED",
                    "Error: " + e.getMessage());

            return CompletableFuture.completedFuture(jobId);
        }
    }

    /**
     * Batch export vehicles to CSV
     */
    @Async
    public CompletableFuture<String> exportVehiclesToCsv(User user, Map<String, Object> filters) {
        String jobId = UUID.randomUUID().toString();
        BatchJobStatus status = new BatchJobStatus(jobId, "VEHICLE_EXPORT", "RUNNING");
        batchJobs.put(jobId, status);

        try {
            log.info("Starting vehicle batch export for user: {}", user.getEmail());

            // Get vehicles based on filters
            List<Car> vehicles = getVehiclesForExport(filters);
            status.setTotalRecords(vehicles.size());

            // Generate CSV content
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("ID,Make,Model,Year,Price,Mileage,FuelType,Transmission,Location,Description\n");

            AtomicInteger processedCount = new AtomicInteger(0);

            vehicles.forEach(vehicle -> {
                csvContent.append(String.format("%d,%s,%s,%d,%s,%d,%s,%s,%s,\"%s\"\n",
                        vehicle.getId(),
                        vehicle.getMake(),
                        vehicle.getModel(),
                        vehicle.getYear(),
                        vehicle.getPrice(),
                        vehicle.getMileage(),
                        vehicle.getFuelType(),
                        vehicle.getTransmission(),
                        vehicle.getLocation(),
                        vehicle.getDescription().replace("\"", "\"\"")));

                status.setProcessedRecords(processedCount.incrementAndGet());
            });

            // Upload CSV file to storage
            byte[] csvBytes = csvContent.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String fileName = "vehicles_export_" + jobId + ".csv";
            
            com.carselling.oldcar.dto.file.FileUploadResponse uploadResponse = fileUploadService.uploadFile(
                    csvBytes, 
                    fileName, 
                    "text/csv", 
                    "exports/" + user.getId(), 
                    user, 
                    com.carselling.oldcar.model.ResourceType.OTHER, 
                    user.getId()
            );

            status.setStatus("COMPLETED");
            status.setDownloadUrl(uploadResponse.getFileUrl());
            status.setCompletedAt(LocalDateTime.now());

            // Send completion notification with download link
            String details = String.format("Export of %d vehicles is ready for download.", vehicles.size());
            notificationService.sendBatchJobCompletionNotification(user, "Vehicle Export", "COMPLETED", details);

            log.info("Vehicle Export Ready for user {}: {}", user.getId(), details);

            log.info("Completed vehicle batch export. Total records: {}", vehicles.size());

            return CompletableFuture.completedFuture(jobId);

        } catch (Exception e) {
            log.error("Error during batch vehicle export: {}", e.getMessage(), e);
            status.setStatus("FAILED");
            status.setErrorMessage(e.getMessage());
            status.setCompletedAt(LocalDateTime.now());

            notificationService.sendBatchJobCompletionNotification(user, "Vehicle Export", "FAILED",
                    "Error: " + e.getMessage());

            return CompletableFuture.completedFuture(jobId);
        }
    }

    /**
     * Async image processing for vehicle galleries
     */
    @Async
    public CompletableFuture<Void> processVehicleImages(Long vehicleId, List<MultipartFile> images) {
        try {
            log.info("Starting async image processing for vehicle ID: {}", vehicleId);

            List<String> processedImageUrls = new ArrayList<>();

            for (MultipartFile image : images) {
                // Resize and optimize images
                MultipartFile resizedImage = resizeImage(image, 800, 600);
                MultipartFile thumbnailImage = resizeImage(image, 300, 200);

                // Upload original, resized, and thumbnail versions
                String originalUrl = fileUploadService.uploadFile(image, "vehicles/" + vehicleId + "/original/", 1L)
                        .getFileUrl();
                String resizedUrl = fileUploadService
                        .uploadFile(resizedImage, "vehicles/" + vehicleId + "/resized/", 1L).getFileUrl();
                String thumbnailUrl = fileUploadService
                        .uploadFile(thumbnailImage, "vehicles/" + vehicleId + "/thumbnails/", 1L).getFileUrl();

                processedImageUrls.add(originalUrl);

                log.debug("Processed image for vehicle {}: original={}, resized={}, thumbnail={}",
                        vehicleId, originalUrl, resizedUrl, thumbnailUrl);
            }

            // Update vehicle with processed image URLs
            updateVehicleImages(vehicleId, processedImageUrls);

            log.info("Completed async image processing for vehicle ID: {}", vehicleId);

        } catch (Exception e) {
            log.error("Error during async image processing for vehicle {}: {}", vehicleId, e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Scheduled cleanup of completed batch jobs
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    public void cleanupCompletedBatchJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        batchJobs.entrySet().removeIf(entry -> {
            BatchJobStatus status = entry.getValue();
            return status.getCompletedAt() != null && status.getCompletedAt().isBefore(cutoff);
        });

        log.info("Cleaned up old batch jobs. Active jobs: {}", batchJobs.size());
    }

    /**
     * Get batch job status
     */
    public BatchJobStatus getBatchJobStatus(String jobId) {
        return batchJobs.get(jobId);
    }

    /**
     * Get all batch jobs for a user
     */
    public List<BatchJobStatus> getUserBatchJobs(String userId) {
        return batchJobs.values().stream()
                .filter(job -> userId.equals(job.getUserId()))
                .collect(Collectors.toList());
    }

    // Private helper methods

    private List<VehicleImportRow> parseCsvFile(MultipartFile file) throws IOException {
        List<VehicleImportRow> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue; // Skip header row
                }

                String[] fields = line.split(",");
                if (fields.length >= 8) { // Minimum required fields
                    VehicleImportRow row = new VehicleImportRow(
                            fields[0].trim(), // make
                            fields[1].trim(), // model
                            Integer.parseInt(fields[2].trim()), // year
                            new BigDecimal(fields[3].trim()), // price
                            Integer.parseInt(fields[4].trim()), // mileage
                            fields[5].trim(), // fuelType
                            fields[6].trim(), // transmission
                            fields[7].trim(), // location
                            fields.length > 8 ? fields[8].trim() : "" // description
                    );
                    rows.add(row);
                }
            }
        }

        return rows;
    }

    @Transactional
    private void processBatch(List<VehicleImportRow> batch, User user,
            AtomicInteger processedCount, AtomicInteger successCount,
            AtomicInteger errorCount, List<String> errors) {
        for (VehicleImportRow row : batch) {
            try {
                // Validate row data
                validateVehicleImportRow(row);

                // Create vehicle entity
                Car vehicle = Car.builder()
                        .make(row.getMake())
                        .model(row.getModel())
                        .year(row.getYear())
                        .price(row.getPrice())
                        .mileage(row.getMileage())
                        .fuelType(row.getFuelType())
                        .transmission(row.getTransmission())
                        .description(row.getDescription())
                        .owner(user)
                        .isActive(true)
                        .build();

                vehicleRepository.save(vehicle);
                successCount.incrementAndGet();

            } catch (Exception e) {
                errorCount.incrementAndGet();
                errors.add(String.format("Row %d: %s", processedCount.get() + 1, e.getMessage()));
                log.warn("Error processing import row: {}", e.getMessage());
            }

            processedCount.incrementAndGet();
        }
    }

    private void validateVehicleImportRow(VehicleImportRow row) {
        if (row.getMake() == null || row.getMake().trim().isEmpty()) {
            throw new IllegalArgumentException("Make is required");
        }
        if (row.getModel() == null || row.getModel().trim().isEmpty()) {
            throw new IllegalArgumentException("Model is required");
        }
        if (row.getYear() < 1900 || row.getYear() > LocalDateTime.now().getYear() + 1) {
            throw new IllegalArgumentException("Invalid year");
        }
        if (row.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }

    private List<Car> getVehiclesForExport(Map<String, Object> filters) {
        // Implementation would use filters to query vehicles
        // For now, return all active vehicles
        return vehicleRepository.findByIsActiveTrue();
    }

    private MultipartFile resizeImage(MultipartFile image, int width, int height) {
        // In a real implementation, this would use ImageIO or a library like
        // Thumbnailator
        // to resize the image. For now, we'll return the original image.
        return image;
    }

    private void updateVehicleImages(Long vehicleId, List<String> imageUrls) {
        vehicleRepository.findById(vehicleId).ifPresent(vehicle -> {
            // For now, we'll set the first image as the main image URL
            if (!imageUrls.isEmpty()) {
                vehicle.setImageUrl(imageUrls.get(0));
            }
            vehicleRepository.save(vehicle);
        });
    }

    // Inner classes for data structures

    public static class BatchJobStatus {
        private String jobId;
        private String jobType;
        private String status;
        private String userId;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private int totalRecords;
        private int processedRecords;
        private int successCount;
        private int errorCount;
        private List<String> errors;
        private String errorMessage;
        private String downloadUrl;

        public BatchJobStatus(String jobId, String jobType, String status) {
            this.jobId = jobId;
            this.jobType = jobType;
            this.status = status;
            this.startedAt = LocalDateTime.now();
            this.errors = new ArrayList<>();
        }

        // Getters and setters
        public String getJobId() {
            return jobId;
        }

        public String getJobType() {
            return jobType;
        }

        public String getStatus() {
            return status;
        }

        public String getUserId() {
            return userId;
        }

        public LocalDateTime getStartedAt() {
            return startedAt;
        }

        public LocalDateTime getCompletedAt() {
            return completedAt;
        }

        public int getTotalRecords() {
            return totalRecords;
        }

        public int getProcessedRecords() {
            return processedRecords;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getDownloadUrl() {
            return downloadUrl;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public void setCompletedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
        }

        public void setTotalRecords(int totalRecords) {
            this.totalRecords = totalRecords;
        }

        public void setProcessedRecords(int processedRecords) {
            this.processedRecords = processedRecords;
        }

        public void setSuccessCount(int successCount) {
            this.successCount = successCount;
        }

        public void setErrorCount(int errorCount) {
            this.errorCount = errorCount;
        }

        public void setErrors(List<String> errors) {
            this.errors = errors;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public void setDownloadUrl(String downloadUrl) {
            this.downloadUrl = downloadUrl;
        }
    }

    public static class VehicleImportRow {
        private String make;
        private String model;
        private Integer year;
        private BigDecimal price;
        private Integer mileage;
        private String fuelType;
        private String transmission;
        private String location;
        private String description;

        public VehicleImportRow(String make, String model, Integer year, BigDecimal price,
                Integer mileage, String fuelType, String transmission,
                String location, String description) {
            this.make = make;
            this.model = model;
            this.year = year;
            this.price = price;
            this.mileage = mileage;
            this.fuelType = fuelType;
            this.transmission = transmission;
            this.location = location;
            this.description = description;
        }

        // Getters
        public String getMake() {
            return make;
        }

        public String getModel() {
            return model;
        }

        public Integer getYear() {
            return year;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public Integer getMileage() {
            return mileage;
        }

        public String getFuelType() {
            return fuelType;
        }

        public String getTransmission() {
            return transmission;
        }

        public String getLocation() {
            return location;
        }

        public String getDescription() {
            return description;
        }
    }
}
