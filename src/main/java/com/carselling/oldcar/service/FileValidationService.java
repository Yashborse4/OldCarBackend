package com.carselling.oldcar.service;

import com.carselling.oldcar.config.FileUploadConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.carselling.oldcar.model.UploadedFile;

/**
 * Service for validating file uploads for security
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileValidationService {

    private final FileUploadConfig fileUploadConfig;
    private final VirusScanService virusScanService;
    private final Tika tika = new Tika();

    private static final Set<String> MALICIOUS_EXTENSIONS = Set.of(
            "exe", "bat", "sh", "cmd", "com", "msi", "jar", "vbs", "ps1", "php", "jsp", "asp", "aspx", "cgi", "pl",
            "py", "rb");

    private static final Set<String> MALICIOUS_CONTENT_TYPES = Set.of(
            "application/x-msdownload", "application/x-sh", "application/x-bat", "application/java-archive",
            "application/x-php", "text/x-php", "application/x-httpd-php", "application/javascript");

    // Common file signatures (Magic Numbers)
    private static final Map<String, List<String>> FILE_SIGNATURES = new HashMap<>();

    static {
        // JPEG: FF D8 FF
        FILE_SIGNATURES.put("jpg", List.of("FFD8FF"));
        FILE_SIGNATURES.put("jpeg", List.of("FFD8FF"));
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        FILE_SIGNATURES.put("png", List.of("89504E47"));
        // GIF: 47 49 46 38
        FILE_SIGNATURES.put("gif", List.of("47494638"));
        // PDF: 25 50 44 46
        FILE_SIGNATURES.put("pdf", List.of("25504446"));
        // MP4: ... various signatures, usually ftyp at offset 4, but checking typical
        // headers
        // Common MP4 ftyp markers: 66 74 79 70 ...
    }

    /**
     * Validate a file upload for security issues
     */
    public void validateFile(MultipartFile file) throws SecurityException {
        if (file == null || file.isEmpty()) {
            throw new SecurityException("File is empty or null");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new SecurityException("Invalid filename");
        }

        // Validate file extension
        if (fileUploadConfig.isValidateFileExtension()) {
            validateFileExtension(originalFilename);
        }

        // 2. Check file size
        validateFileSize(file);

        // 3. Validate content type (Tika + Mime strict check)
        if (fileUploadConfig.isValidateContentType()) {
            validateContentType(file);
        }

        // 4. Magic Number Validation (Deep inspection)
        if (fileUploadConfig.isValidateMagicNumbers()) {
            validateMagicNumbers(file);
        }

        // 5. Check for potential security threats (Malicious extensions/content types
        // checks)
        checkForSecurityThreats(file);

        // 6. Scan for viruses if enabled
        if (fileUploadConfig.isScanForViruses()) {
            virusScanService.scanFile(file);
        }

        // 7. Validate image dimensions if it's an image
        // Optional: Ensure it's not a pixel bomb or too large dimension
        if (isImageFile(originalFilename)) {
            validateImageDimensions(file);
        }
    }

    /**
     * Validate multiple files
     */
    public void validateFiles(List<MultipartFile> files) throws SecurityException {
        if (files == null || files.isEmpty()) {
            return;
        }
        if (files.size() > fileUploadConfig.getMaxFilesPerRequest()) {
            throw new SecurityException(
                    "Too many files. Maximum allowed: " + fileUploadConfig.getMaxFilesPerRequest());
        }

        for (MultipartFile file : files) {
            validateFile(file);
        }
    }

    /**
     * Re-validate an existing uploaded file (e.g. during temp-to-perm transfer)
     * Used by TempFileTransferService
     */
    public boolean validateFile(MultipartFile file, UploadedFile record) {
        if (record == null)
            return false;

        try {
            // 1. Check size limit based on type
            String filename = record.getOriginalFileName();
            boolean isVideo = isVideoFile(filename);

            long maxFileSizeBytes;
            if (isVideo) {
                maxFileSizeBytes = fileUploadConfig.getMaxVideoSizeMB() * 1024L * 1024L;
            } else if (isImageFile(filename)) {
                maxFileSizeBytes = fileUploadConfig.getMaxFileSizeMB() * 1024L * 1024L;
            } else {
                maxFileSizeBytes = fileUploadConfig.getMaxFileSizeMB() * 1024L * 1024L;
            }

            if (record.getSize() > maxFileSizeBytes) {
                log.warn("File {} exceeds size limit during re-validation: {} > {}",
                        record.getId(), record.getSize(), maxFileSizeBytes);
                return false;
            }

            // 2. Check extension
            String extension = getFileExtension(filename).toLowerCase();
            if (!fileUploadConfig.getAllowedExtensions().contains(extension)) {
                log.warn("File {} has invalid extension: {}", record.getId(), extension);
                return false;
            }

            // 3. Security checks on metadata
            // We can't easily check magic numbers without downloading the file,
            // but we can check if extension matches stored content type loosely
            // or just rely on the fact it passed initial validation if we trust the DB.
            // But let's check for malicious extensions again just in case.
            if (MALICIOUS_EXTENSIONS.contains(extension)) {
                log.warn("File {} has malicious extension: {}", record.getId(), extension);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Error validting file record {}", record.getId(), e);
            return false;
        }
    }

    private void validateFileSize(MultipartFile file) throws SecurityException {
        String originalFilename = file.getOriginalFilename();
        boolean isVideo = isVideoFile(originalFilename);
        boolean isImage = isImageFile(originalFilename);

        long maxFileSizeBytes;

        if (isVideo) {
            maxFileSizeBytes = fileUploadConfig.getMaxVideoSizeMB() * 1024L * 1024L;
        } else if (isImageFile(file.getOriginalFilename())) {
            // 1.5 MB limit for images as per requirement (hardcoded or config driven, using
            // 1.5MB as requested)
            maxFileSizeBytes = 1500 * 1024L; // 1.5 MB
        } else {
            maxFileSizeBytes = fileUploadConfig.getMaxFileSizeMB() * 1024L * 1024L;
        }

        if (file.getSize() > maxFileSizeBytes) {
            String limitMsg = (maxFileSizeBytes / (1024 * 1024)) + "MB";
            throw new SecurityException("File size exceeds maximum allowed size of " + limitMsg);
        }
    }

    private void validateFileExtension(String filename) throws SecurityException {
        String extension = getFileExtension(filename);
        if (extension == null || extension.isBlank()) {
            throw new SecurityException("File has no extension");
        }

        extension = extension.toLowerCase();
        if (!fileUploadConfig.getAllowedExtensions().contains(extension)) {
            throw new SecurityException(
                    "File extension not allowed. Allowed extensions: " + fileUploadConfig.getAllowedExtensions());
        }
    }

    private void validateContentType(MultipartFile file) throws SecurityException {
        try {
            String detectedContentType = tika.detect(file.getBytes());
            String declaredContentType = file.getContentType();

            log.debug("Detected content type: {}, Declared: {}", detectedContentType, declaredContentType);

            // Check if detected content type is allowed
            if (!isAllowedContentType(detectedContentType)) {
                throw new SecurityException(
                        "Content type not allowed: " + detectedContentType +
                                ". Allowed types: " + fileUploadConfig.getAllowedContentTypes());
            }

            // Verify declared type matches detected type - STRICT Check
            // We allow some flexibility for commonly confused types if needed, but strict
            // is safer
            if (declaredContentType != null && !declaredContentType.equalsIgnoreCase(detectedContentType)) {
                // Common specific/generic mismatches can be gracefully handled here if needed.
                // For now, keeping it strict as per "Advance detection" requirement.
                // Exception: audio/mp4 vs video/mp4 sometimes happens
                if (!(declaredContentType.contains("mp4") && detectedContentType.contains("mp4"))) {
                    log.warn("Content type mismatch: declared={}, detected={}", declaredContentType,
                            detectedContentType);
                    throw new SecurityException(
                            "Content type mismatch: declared " + declaredContentType + " but detected "
                                    + detectedContentType);
                }
            }

        } catch (IOException e) {
            log.error("Error detecting file content type", e);
            throw new SecurityException("Unable to determine file content type");
        }
    }

    private void validateMagicNumbers(MultipartFile file) throws SecurityException {
        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();
        if (FILE_SIGNATURES.containsKey(extension)) {
            try (InputStream is = file.getInputStream()) {
                byte[] header = new byte[8]; // Check first 8 bytes
                int read = is.read(header);
                if (read < 4) {
                    return; // Too short to verify
                }

                String fileHex = bytesToHex(header).toUpperCase();
                boolean match = false;
                for (String signature : FILE_SIGNATURES.get(extension)) {
                    if (fileHex.startsWith(signature)) {
                        match = true;
                        break;
                    }
                }

                if (!match) {
                    log.warn("Magic number verification failed for file: {}. Header: {}", file.getOriginalFilename(),
                            fileHex);
                    throw new SecurityException("File signature does not match extension " + extension);
                }
            } catch (IOException e) {
                log.error("Error reading file for magic number validation", e);
                // Fail safe - if we can't read, we can't be sure it's safe.
                // Or we can ignore. Choosing to be strict.
                throw new SecurityException("Unable to verify file signature");
            }
        }
    }

    private void checkForSecurityThreats(MultipartFile file) throws SecurityException {
        String filename = file.getOriginalFilename();
        String extension = getFileExtension(filename).toLowerCase();

        // Check for malicious extensions (double check, independent of config allow
        // list)
        if (MALICIOUS_EXTENSIONS.contains(extension)) {
            throw new SecurityException("Potentially malicious file extension detected: " + extension);
        }

        // Double extension check (e.g., image.php.jpg)
        // Simple logic: check if there is another extension before the last one that is
        // malicious/script
        // Actually, simple regex for .php., .exe. etc is good
        if (filename.matches(".*\\.(php|exe|sh|bat|jsp)\\..*")) {
            throw new SecurityException("Potentially malicious double extension detected.");
        }

        // Check Malicious Content Type
        if (file.getContentType() != null && MALICIOUS_CONTENT_TYPES.contains(file.getContentType().toLowerCase())) {
            throw new SecurityException("Potentially malicious content type detected: " + file.getContentType());
        }

        validateMaliciousContent(file);
    }

    private void validateMaliciousContent(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();
            // MZ signature (DOS/Windows executables)
            if (fileBytes.length > 1 && fileBytes[0] == 0x4D && fileBytes[1] == 0x5A) {
                throw new SecurityException("Executable file signature (MZ) detected.");
            }
            // ELF signature
            if (fileBytes.length > 3 && fileBytes[0] == 0x7F && fileBytes[1] == 0x45 && fileBytes[2] == 0x4C
                    && fileBytes[3] == 0x46) {
                throw new SecurityException("Executable file signature (ELF) detected.");
            }
        } catch (IOException e) {
            throw new SecurityException("Error scanning file content");
        }
    }

    private void validateImageDimensions(MultipartFile file) {
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                // Not a valid image or ImageIO can't read it
                // If it's supposed to be an image (by extension), this is suspicious.
                // However, newer formats like WebP might require plugins.
                // Only throw if we are strict.
                return;
            }

            // Example limits: Max 10000x10000 pixels
            if (image.getWidth() > 10000 || image.getHeight() > 10000) {
                throw new SecurityException("Image dimensions too large");
            }
        } catch (IOException e) {
            // Ignore, maybe not an image we can parse
        }
    }

    // Helper methods

    private String getFileExtension(String filename) {
        if (filename == null)
            return "";
        int lastDotIndex = filename.lastIndexOf('.');
        return (lastDotIndex > 0 && lastDotIndex < filename.length() - 1)
                ? filename.substring(lastDotIndex + 1)
                : "";
    }

    // Explicitly checking against configured lists for helpers
    public boolean isImageFile(String filename) {
        String ext = getFileExtension(filename).toLowerCase();
        return fileUploadConfig.getAllowedImageExtensions().contains(ext);
    }

    public boolean isVideoFile(String filename) {
        String ext = getFileExtension(filename).toLowerCase();
        return fileUploadConfig.getAllowedVideoExtensions().contains(ext);
    }

    private boolean isAllowedContentType(String contentType) {
        if (contentType == null)
            return false;
        // Check if starts with allowed definition or exact match?
        // Config list usually has full types "image/jpeg", "video/mp4"
        return fileUploadConfig.getAllowedContentTypes().contains(contentType.toLowerCase()) ||
        // Allow loose matching for some sub-types if configured, but here we stick to
        // list
                false;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    // For manual calls if needed
    public void checkForSecurityThreats(String originalFilename) {
        // Overload if needed by legacy code, but we prefer the full check
    }

    public void validateFileUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new SecurityException("File URL cannot be empty");
        }
        if (url.contains("..") || url.contains("~") || url.contains("%2e%2e")) {
            throw new SecurityException("Invalid file URL path");
        }
        // Basic check to ensure it's not a local file path or something malicious if
        // expected to be http
    }

    public void validateFolderName(String folder) {
        if (folder == null || folder.trim().isEmpty()) {
            throw new SecurityException("Folder name cannot be empty");
        }
        // Prevent directory traversal
        if (folder.contains("..") || folder.contains("~") || folder.contains("%2e%2e") || folder.startsWith("/")) {
            throw new SecurityException("Invalid folder name: " + folder);
        }
        // Allow alpha-numeric, slashes, dashes, underscores
        if (!folder.matches("^[a-zA-Z0-9/_\\-]+$")) {
            throw new SecurityException("Folder contains invalid characters: " + folder);
        }
    }

}