package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.TemporaryFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemporaryFileRepository extends JpaRepository<TemporaryFile, Long> {
        Optional<TemporaryFile> findByFileHashAndUploadedById(String fileHash, Long uploadedById);

        Optional<TemporaryFile> findByFileUrl(String fileUrl);

        java.util.List<TemporaryFile> findByCreatedAtBefore(java.time.LocalDateTime createdAt);

        java.util.List<TemporaryFile> findByStorageStatusInAndCreatedAtBefore(
                        java.util.Collection<com.carselling.oldcar.model.StorageStatus> statuses,
                        java.time.LocalDateTime createdAt);

        void deleteByFileUrlStartingWith(String prefix);

        java.util.List<TemporaryFile> findByCarIdAndStorageStatus(Long carId,
                        com.carselling.oldcar.model.StorageStatus storageStatus);

        // Bulk fetch for Startup Runner usage to avoid N+1
        java.util.List<TemporaryFile> findByCarIdInAndStorageStatus(List<Long> carIds,
                        com.carselling.oldcar.model.StorageStatus storageStatus);

        // Find FAILED files due for retry (per-file retry)
        java.util.List<TemporaryFile> findTop20ByStorageStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
                        com.carselling.oldcar.model.StorageStatus status, java.time.LocalDateTime now);

        // Find stale temp files for cleanup (uploaded but never finalized)
        java.util.List<TemporaryFile> findByStorageStatusAndCreatedAtBefore(
                        com.carselling.oldcar.model.StorageStatus status, java.time.LocalDateTime threshold);
}
