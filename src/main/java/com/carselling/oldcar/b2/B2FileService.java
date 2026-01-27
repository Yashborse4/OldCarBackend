package com.carselling.oldcar.b2;

import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.model.UploadedFile;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.model.TemporaryFile;
import com.carselling.oldcar.repository.UploadedFileRepository;
import com.carselling.oldcar.repository.TemporaryFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class B2FileService {

    private final B2Client b2Client;
    private final UploadedFileRepository uploadedFileRepository;
    private final B2Properties properties;

    private final java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors
            .newFixedThreadPool(10);

    public FileUploadResponse uploadFile(MultipartFile file, String folder, User uploader, ResourceType ownerType,
            Long ownerId) throws IOException {

        // 1. Calculate Checksum (SHA-1) for Idempotency
        String fileHash = calculateSha1(file);

        // 2. Check for Duplicate (Idempotency)
        Optional<UploadedFile> existing = uploadedFileRepository.findByFileHashAndUploadedById(fileHash,
                uploader.getId());
        if (existing.isPresent()) {
            UploadedFile existingFile = existing.get();
            log.info("Idempotency hit: Returning existing file {} (hash: {}) for user {}", existingFile.getFileName(),
                    fileHash, uploader.getId());
            return FileUploadResponse.builder()
                    .fileName(existingFile.getFileName())
                    .originalFileName(existingFile.getOriginalFileName())
                    .fileUrl(existingFile.getFileUrl())
                    .fileSize(existingFile.getSize())
                    .contentType(existingFile.getContentType())
                    .folder(folder) // Note: folder might be different, but content is same.
                    .uploadedAt(existingFile.getCreatedAt())
                    .build();
        }

        String originalFileName = file.getOriginalFilename();
        String uniqueFileName = generateUniqueFileName(originalFileName, uploader.getId());
        String fullPath = folder + "/" + uniqueFileName;

        // B2 requires the file name to be URL-encoded for the upload request header if
        // it contains special chars
        // But the path itself is the "file name" in B2 concept (including folders)
        String b2FileName = URLEncoder.encode(fullPath, StandardCharsets.UTF_8).replace("+", "%20"); // Simple encoding
                                                                                                     // match

        log.info("Uploading file to B2: {}", fullPath);

        // Build ownership metadata for B2 tagging
        java.util.Map<String, String> fileMetadata = new java.util.HashMap<>();
        fileMetadata.put("uploaded-by-user-id", String.valueOf(uploader.getId()));
        fileMetadata.put("owner-type", ownerType.name());
        if (ownerId != null) {
            fileMetadata.put("owner-id", String.valueOf(ownerId));
        }
        fileMetadata.put("upload-timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        b2Client.uploadFile(b2FileName, file.getBytes(),
                file.getContentType(), fileMetadata);

        // Construct CDN URL
        // User Requirement: "domain name + file name" directly hit Cloudflare.
        // Assuming Cloudflare is configured to map <cdnDomain>/path -> <b2Bucket>/path
        // So we strictly use cdnDomain + "/" + fullPath (which is the B2 file name/key)

        // Ensure no double slashes if domain has trailing slash
        String domain = properties.getCdnDomain();
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }

        // fullPath is "folder/filename"
        // b2FileName was encoded, but for the public URL we want the logical path
        // (browser/CDN handles encoding usually,
        // but let's be safe: if we store "cars/1/foo.jpg", URL is
        // "domain.com/cars/1/foo.jpg")

        String publicUrl = domain + "/" + fullPath;

        // Save metadata
        UploadedFile uploadedFile = UploadedFile.builder()
                .fileUrl(publicUrl)
                .fileName(uniqueFileName)
                .originalFileName(originalFileName)
                .contentType(file.getContentType())
                .size(file.getSize())
                .uploadedBy(uploader)
                .ownerType(ownerType)
                .ownerId(ownerId)
                .fileHash(fileHash) // Save hash for future idempotency
                .build();

        uploadedFileRepository.save(uploadedFile);

        return FileUploadResponse.builder()
                .fileName(uniqueFileName)
                .originalFileName(originalFileName)
                .fileUrl(publicUrl)
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .folder(folder)
                .uploadedAt(LocalDateTime.now())
                .build();
    }

    public List<FileUploadResponse> uploadMultipleFiles(List<MultipartFile> files, String folder,
            User uploader, ResourceType ownerType, Long ownerId) {

        List<java.util.concurrent.CompletableFuture<FileUploadResponse>> futures = files.stream()
                .map(file -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    try {
                        return uploadFile(file, folder, uploader, ownerType, ownerId);
                    } catch (IOException e) {
                        log.error("Failed to upload file: {}", file.getOriginalFilename(), e);
                        return FileUploadResponse.builder()
                                .originalFileName(file.getOriginalFilename())
                                .fileUrl(null)
                                .fileName("FAILED: " + e.getMessage())
                                .build();
                    }
                }, executorService))
                .collect(java.util.stream.Collectors.toList());

        return futures.stream()
                .map(java.util.concurrent.CompletableFuture::join)
                .collect(java.util.stream.Collectors.toList());
    }

    private String calculateSha1(MultipartFile file) throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(file.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not found", e);
        }
    }

    public boolean deleteFile(String fileUrl) {
        // Implementation requires extracting fileName/ID.
        // Skipping strict delete logic for now as focus is upload migration.
        return false;
    }

    // ============================================================================================
    // DIRECT UPLOAD METHODS (Serverless FLow)
    // ============================================================================================

    @lombok.Data
    @lombok.Builder
    public static class DirectUploadInitResponse {
        private String uploadUrl;
        private String authorizationToken;
        private String fileName;
        private String fileUrl; // Public URL (future)
    }

    private final TemporaryFileRepository temporaryFileRepository;

    public DirectUploadInitResponse initDirectUpload(String originalFileName, String folder, User uploader) {
        // Enforce temp folder for direct uploads
        // Format: temp/{userId}/
        String tempFolder = "temp/" + uploader.getId();

        String uniqueFileName = generateUniqueFileName(originalFileName, uploader.getId());
        String fullPath = tempFolder + "/" + uniqueFileName;

        // B2 requires the file name to be URL-encoded for the upload request header if
        // it contains special chars
        // But the path itself is the "file name" in B2 concept (including folders)
        String b2FileName = URLEncoder.encode(fullPath, StandardCharsets.UTF_8).replace("+", "%20");

        // 1. Get One-Time Upload URL from B2
        B2Client.GetUploadUrlResponse uploadUrl = b2Client.getUploadUrl();

        // 2. Construct future public URL (though it might not be public yet for temp)
        // Note: Temp files might need a different path or authentication if they
        // shouldn't be public.
        // For now, assuming standard CDN path but we might restrict access logic
        // elsewhere or via B2 buckets.
        // B2 bucket is usually either all public or all private. If public, "temp/" is
        // public.
        // We will assume "temp/" is public for the user to verify (preview) before
        // finalizing.
        String domain = properties.getCdnDomain();
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        String publicUrl = domain + "/" + fullPath;

        return DirectUploadInitResponse.builder()
                .uploadUrl(uploadUrl.getUploadUrl())
                .authorizationToken(uploadUrl.getAuthorizationToken())
                .fileName(b2FileName) // Client puts this in X-Bz-File-Name
                .fileUrl(publicUrl)
                .build();
    }

    public Object completeDirectUpload(String b2FileName, String fileId, User uploader, ResourceType ownerType,
            Long ownerId, Long fileSize, String originalFileName, String contentType) {
        // 1. Get File Info from B2 to verify and get Hash (contentSha1)
        B2Client.B2FileInfo fileInfo = b2Client.getFileInfo(fileId);
        String fileHash = fileInfo.getContentSha1();

        // 2. DUPLICATE DETECTION
        // Check if this hash already exists in PERMANENT files for this user (or
        // globally if public)
        // Checking mostly for this user to avoid re-uploading same file
        // NOTE: hash might be "none" for large files uploaded via multi-part in B2,
        // handle that?
        // Basic implementation assumes small-ish files with SHA1.

        if (fileHash != null && !fileHash.equals("none")) {
            Optional<UploadedFile> existing = uploadedFileRepository.findByFileHashAndUploadedById(fileHash,
                    uploader.getId());
            if (existing.isPresent()) {
                log.info("Duplicate file detected for user {}: {}", uploader.getId(), fileHash);
                // We should ideally DELETE the temp file just uploaded to save space, and
                // return the existing one.
                // Or just point to existing.
                // Let's delete the 'temp' file that was just uploaded.
                try {
                    b2Client.deleteFileVersion(b2FileName, fileId);
                } catch (Exception e) {
                    log.error("Failed to delete duplicate temp file from B2: {}", b2FileName, e);
                }

                // Return existing file as "UploadedFile" directly?
                // The caller expects confirmation. We can reuse.
                return existing.get();
            }
        }

        // 3. Save as TemporaryFile
        String decodedPath = java.net.URLDecoder.decode(b2FileName, StandardCharsets.UTF_8);
        String fileName = decodedPath.contains("/") ? decodedPath.substring(decodedPath.lastIndexOf("/") + 1)
                : decodedPath;

        String domain = properties.getCdnDomain();
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        String publicUrl = domain + "/" + decodedPath;

        TemporaryFile tempFile = TemporaryFile.builder()
                .fileUrl(publicUrl)
                .fileId(fileId) // Store B2 File ID
                .fileName(fileName)
                .originalFileName(originalFileName != null ? originalFileName : fileName)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .fileSize(fileSize != null ? fileSize : 0L)
                .uploadedBy(uploader)
                .fileHash(fileHash)
                .build();

        return temporaryFileRepository.save(tempFile);
    }

    private String generateUniqueFileName(String originalFilename, Long userId) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }
        return String.format("%d_%s_%s.%s", userId, timestamp, uuid, extension);
    }
}
