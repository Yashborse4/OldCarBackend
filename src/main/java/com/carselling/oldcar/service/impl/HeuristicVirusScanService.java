package com.carselling.oldcar.service.impl;

import com.carselling.oldcar.service.file.VirusScanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Heuristic implementation of VirusScanService.
 * Uses pattern matching and entropy analysis to detect potential threats.
 */
@Service
@Slf4j
public class HeuristicVirusScanService implements VirusScanService {

    private static final String[] MALICIOUS_PATTERNS = {
            "<script", "javascript:", "vbscript:", "onload=", "onerror=",
            "eval(", "exec(", "system(", "shell_exec", "passthru",
            "<?php", "<%", "<iframe", "<object", "<embed",
            "cmd.exe", "/bin/sh", "/bin/bash", "powershell",
            "reflect.assembly", "wscript.shell",
            "base64_decode", "gzinflate", "str_rot13",
            "runtime.getruntime", "processbuilder"
    };

    private static final String[] SCRIPT_INDICATORS = {
            "<script", "javascript:", "eval(", "function(", "alert(", "document."
    };

    @Override
    public void scanFile(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            scanBytes(fileBytes);

            // Additional check for polyglot files in images if needed
            // (scanBytes covers general patterns, but context matters)
            String filename = file.getOriginalFilename();
            if (filename != null && isImageFile(filename) && containsScriptContent(fileBytes)) {
                throw new SecurityException("Image file contains embedded scripts");
            }

        } catch (IOException e) {
            log.error("Error detecting virus/malware for file: {}", file.getOriginalFilename(), e);
            throw new SecurityException("Unable to scan file for viruses");
        }
    }

    @Override
    public void scanBytes(byte[] content) {
        // Check for common malicious signatures
        if (containsMaliciousSignatures(content)) {
            throw new SecurityException("File contains potential malware signatures");
        }

        // Check file entropy (high entropy might indicate encryption/obfuscation)
        if (hasHighEntropy(content)) {
            log.warn("File has high entropy, potential obfuscation detected");
            // We usually don't block solely on entropy as compressed files (zip, jpg) have
            // high entropy,
            // but we log it for audit.
        }
    }

    private boolean containsMaliciousSignatures(byte[] fileBytes) {
        String content = new String(fileBytes); // Naive string conversion, but effective for script tags
        String lowerContent = content.toLowerCase();

        for (String pattern : MALICIOUS_PATTERNS) {
            if (lowerContent.contains(pattern.toLowerCase())) {
                log.warn("Malicious pattern detected: {}", pattern);
                return true;
            }
        }
        return false;
    }

    private boolean containsScriptContent(byte[] fileBytes) {
        String content = new String(fileBytes);
        String lowerContent = content.toLowerCase();

        for (String indicator : SCRIPT_INDICATORS) {
            if (lowerContent.contains(indicator.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasHighEntropy(byte[] fileBytes) {
        if (fileBytes.length == 0)
            return false;

        int[] frequency = new int[256];
        for (byte b : fileBytes) {
            frequency[b & 0xFF]++;
        }

        double entropy = 0.0;
        for (int freq : frequency) {
            if (freq > 0) {
                double probability = (double) freq / fileBytes.length;
                entropy -= probability * (Math.log(probability) / Math.log(2));
            }
        }

        // Entropy > 7.5 is suspicious for specific file types, but common for
        // encrypted/compressed.
        return entropy > 7.9; // Raising threshold slightly to avoid false positives on legit compressed media
    }

    private boolean isImageFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".gif") ||
                lower.endsWith(".webp");
    }
}
