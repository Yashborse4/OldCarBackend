package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.TemporaryFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.util.Optional;


@Repository
public interface TemporaryFileRepository extends JpaRepository<TemporaryFile, Long> {
    Optional<TemporaryFile> findByFileHashAndUploadedById(String fileHash, Long uploadedById);

    Optional<TemporaryFile> findByFileUrl(String fileUrl);

    java.util.List<TemporaryFile> findByCreatedAtBefore(java.time.LocalDateTime createdAt);
}
