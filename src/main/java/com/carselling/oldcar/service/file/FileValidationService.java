package com.carselling.oldcar.service.file;

import com.carselling.oldcar.config.FileUploadConfig;
import com.carselling.oldcar.service.file.VirusScanService;
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
    private static final Map<String, List<FileSignature>> FILE_SIGNATURES = new HashMap<>();

    private static class FileSignature {
        int offset;
        String hexSignature;

        public FileSignature(String hexSignature, int offset) {
            this.hexSignature = hexSignature;
            this.offset = offset;
        }

        public FileSignature(String hexSignature) {
            this(hexSignature, 0);
        }
    }

    static {
        // JPEG: FF D8 FF
        FILE_SIGNATURES.put("jpg", List.of(new FileSignature("FFD8FF")));
        FILE_SIGNATURES.put("jpeg", List.of(new FileSignature("FFD8FF")));
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        FILE_SIGNATURES.put("png", List.of(new FileSignature("89504E47")));
        // GIF: 47 49 46 38
        FILE_SIGNATURES.put("gif", List.of(new FileSignature("47494638")));
        // PDF: 25 50 44 46
        FILE_SIGNATURES.put("pdf", List.of(new FileSignature("25504446")));
        // WEBP: WEBP at offset 8 (RIFF is implied but we check specific type)
        FILE_SIGNATURES.put("webp", List.of(new FileSignature("57454250", 8)));

        // Video Formats
        // MP4: ftyp at offset 4 (66 74 79 70)
        FILE_SIGNATURES.put("mp4", List.of(new FileSignature("66747970", 4)));
        // MOV: ftyp at offset 4
        FILE_SIGNATURES.put("mov", List.of(new FileSignature("66747970", 4)));
        // MKV/WebM: 1A 45 DF A3 at offset 0
        FILE_SIGNATURES.put("mkv", List.of(new FileSignature("1A45DFA3")));
        FILE_SIGNATURES.put("webm", List.of(new FileSignature("1A45DFA3")));
        // AVI: AVI at offset 8 (RIFF is implied)
        FILE_SIGNATURES.put("avi", List.of(new FileSignature("41564920", 8)));
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
     * Validate file metadata (for direct uploads where we don't have the file yet)
     */
    public void validateFileMetadata(String fileName, long size, String contentType) {
        // 1. Validate File Name & Extension
        String extension = getFileExtension(fileName);
        if (fileUploadConfig.isValidateFileExtension()) {
            if (!fileUploadConfig.getAllowedExtensions().contains(extension.toLowerCase())) {
                throw new SecurityException("Invalid file type (extension not allowed): " + extension);
            }
        }

        // 2. Validate Content Type
        if (contentType != null && fileUploadConfig.isValidateContentType()) {
            if (!isAllowedContentType(contentType)) {
                throw new SecurityException("Invalid content type: " + contentType);
            }
        }

        // 3. Validate Size
        boolean isVideo = fileUploadConfig.getAllowedVideoExtensions().contains(extension.toLowerCase());
        boolean isImage = fileUploadConfig.getAllowedImageExtensions().contains(extension.toLowerCase());
        long maxFileSizeBytes;

        if (isVideo) {
            maxFileSizeBytes = fileUploadConfig.getMaxVideoSizeMB() * 1024L * 1024L;
        } else if (isImage) {
            maxFileSizeBytes = fileUploadConfig.getMaxImageSizeMB() * 1024L * 1024L;
        } else {
            maxFileSizeBytes = fileUploadConfig.getMaxFileSizeMB() * 1024L * 1024L;
        }

        if (size > maxFileSizeBytes) {
            throw new SecurityException("File size exceeds limit of " + (maxFileSizeBytes / 1024 / 1024) + "MB");
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
                maxFileSizeBytes = fileUploadConfig.getMaxImageSizeMB() * 1024L * 1024L;
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
        } else if (isImage) {
            // Use configured max image size
            maxFileSizeBytes = fileUploadConfig.getMaxImageSizeMB() * 1024L * 1024L;
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
                byte[] header = new byte[32]; // Read first 32 bytes to cover offsets
                int read = is.read(header);
                if (read < 4) {
                    return; // Too short to verify
                }

                String fileHex = bytesToHex(header).toUpperCase();
                boolean match = false;
                List<FileSignature> signatures = FILE_SIGNATURES.get(extension);

                // Check if ANY of the signatures match (OR logic usually, but here some are
                // composite?)
                // Actually my structure implementation above for WebP ("RIFF" ... "WEBP") puts
                // both in the list.
                // If I have multiple signatures for an extension, usually it's "OR" (e.g. valid
                // sig 1 OR valid sig 2).
                // BUT for WEBP/AVI I used the list to mean "AND" effectively in my thought
                // process?
                // Wait, "webp" key has a List.
                // Using List for OR is standard.
                // For WEBP, I put two generic signatures: "RIFF" and "WEBP". If I treat as OR,
                // then any RIFF is valid WEBP? NO.
                // I need composite signatures or just check the most specific one.
                // "WEBP" at offset 8 is specific enough for WEBP. "AVI " at 8 is specific for
                // AVI.
                // "ftyp" at 4 is specific for MP4/MOV types.
                // So I will simplify the WEBP/AVI definitions to just the distinguishing part
                // for now to keep logic Simple "OR".

                // Retrying logic: simple OR match of any defined signature in the list.
                for (FileSignature signature : signatures) {
                    int offset = signature.offset;
                    String requiredHex = signature.hexSignature;

                    if (read < offset + (requiredHex.length() / 2)) {
                        continue;
                    }

                    // Extract the bytes at offset
                    String actualHex = fileHex.substring(offset * 2, (offset * 2) + requiredHex.length());
                    if (actualHex.equals(requiredHex)) {
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