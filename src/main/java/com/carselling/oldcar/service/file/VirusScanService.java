package com.carselling.oldcar.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Interface for virus scanning services.
 * Allows for pluggable implementations (e.g., Heuristic, ClamAV, VirusTotal).
 */
public interface VirusScanService {

    /**
     * Scan a file for viruses and malware.
     * 
     * @param file The file to scan
     * @throws SecurityException if malware is detected or scanning fails
     */
    void scanFile(MultipartFile file);

    /**
     * Scan bytes for viruses and malware.
     * 
     * @param content The byte content to scan
     * @throws SecurityException if malware is detected or scanning fails
     */
    void scanBytes(byte[] content);
}
