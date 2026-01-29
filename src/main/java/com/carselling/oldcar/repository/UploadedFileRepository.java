package com.carselling.oldcar.repository;

import com.carselling.oldcar.model.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {

    java.util.Optional<UploadedFile> findByFileHashAndUploadedById(String fileHash, Long uploadedById);

    java.util.Optional<UploadedFile> findByFileUrl(String fileUrl);
}
