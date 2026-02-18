package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.UploadedFile;
import com.carselling.oldcar.model.StorageStatus;
import com.carselling.oldcar.model.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {

        java.util.Optional<UploadedFile> findByFileHashAndUploadedById(String fileHash, Long uploadedById);

        java.util.Optional<UploadedFile> findByFileUrl(String fileUrl);

        List<UploadedFile> findByStorageStatusAndTempExpiresAtBefore(StorageStatus storageStatus,
                        LocalDateTime expiresAt);

        List<UploadedFile> findByOwnerTypeAndOwnerIdAndStorageStatus(ResourceType ownerType, Long ownerId,
                        StorageStatus storageStatus);

        @Query("SELECT uf FROM UploadedFile uf WHERE uf.storageStatus = :storageStatus AND uf.tempExpiresAt < :expiresAt")
        List<UploadedFile> findExpiredTempFiles(@Param("storageStatus") StorageStatus storageStatus,
                        @Param("expiresAt") LocalDateTime expiresAt);

        void deleteByOwnerTypeAndOwnerId(ResourceType ownerType, Long ownerId);

        List<UploadedFile> findByOwnerTypeAndCreatedAtBeforeAndStorageStatusNot(ResourceType ownerType,
                        LocalDateTime createdAt, StorageStatus storageStatus);
}
