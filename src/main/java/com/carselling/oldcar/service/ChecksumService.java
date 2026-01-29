package com.carselling.oldcar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Service to validate file checksums (MD5) uploaded by clients.
 */
@Service
@Slf4j
public class ChecksumService {

    /**
     * Validates the checksum of an uploaded file against the provided MD5 hash.
     * 
     * @param file             The uploaded file
     * @param expectedChecksum The MD5 checksum provided by the client (hex string)
     * @return true if valid, false if mismatch
     * @throws IOException if file reading fails
     */
    public boolean validateChecksum(MultipartFile file, String expectedChecksum) throws IOException {
        if (expectedChecksum == null || expectedChecksum.trim().isEmpty()) {
            return true; // No checksum provided, skip validation (or could be strict and fail)
        }

        try (InputStream inputStream = file.getInputStream()) {
            String calculatedChecksum = DigestUtils.md5DigestAsHex(inputStream);

            boolean isValid = calculatedChecksum.equalsIgnoreCase(expectedChecksum);
            if (!isValid) {
                log.warn("Checksum mismatch for file: {}. Expected: {}, Calculated: {}",
                        file.getOriginalFilename(), expectedChecksum, calculatedChecksum);
            } else {
                log.debug("Checksum verified for file: {}", file.getOriginalFilename());
            }
            return isValid;
        }
    }
}
