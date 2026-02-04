package com.carselling.oldcar.b2;

import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.model.AccessType;
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

    private final java.util.concurrent.Executor taskExecutor;

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
                    .fileId(existingFile.getFileId())
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

        B2Client.UploadFileResponse b2Response = b2Client.uploadFile(b2FileName, file.getBytes(),
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

        // Determine Access Type
        AccessType accessType = AccessType.PUBLIC;
        if (ownerType == ResourceType.CHAT_ATTACHMENT) {
            accessType = AccessType.PRIVATE;
        } else if (ownerType == ResourceType.OTHER) {
            accessType = AccessType.PRIVATE; // Default safe
        }

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
                .accessType(accessType)
                .fileHash(fileHash) // Save hash for future idempotency
                .fileId(b2Response.getFileId())
                .build();

        uploadedFileRepository.save(uploadedFile);

        return FileUploadResponse.builder()
                .fileName(uniqueFileName)
                .originalFileName(originalFileName)
                .fileUrl(publicUrl)
                .fileId(b2Response.getFileId())
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
                }, taskExecutor))
                .collect(java.util.stream.Collectors.toList());

        return futures.stream()
                .map(java.util.concurrent.CompletableFuture::join)
                .collect(java.util.stream.Collectors.toList());
    }

    private String calculateSha1(MultipartFile file) throws IOException {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            try (java.io.InputStream is = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, n);
                }
            }
            byte[] hash = digest.digest();
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
        if (fileUrl == null || fileUrl.isEmpty()) {
            return false;
        }

        Optional<UploadedFile> fileOpt = uploadedFileRepository.findByFileUrl(fileUrl);
        if (fileOpt.isPresent()) {
            UploadedFile file = fileOpt.get();
            try {
                // If we have a fileId, delete that specific version (safer/cleaner in B2)
                if (file.getFileId() != null) {
                    // Extract B2 File Name (Key) from URL
                    // URL: https://cdn.domain.com/folder/file.ext
                    // Key: folder/file.ext
                    String domain = properties.getCdnDomain();
                    String b2Key = fileUrl.replace(domain + "/", "");
                    if (b2Key.startsWith("/")) {
                        b2Key = b2Key.substring(1);
                    }

                    try {
                        // Decode just in case URL encoding messes up finding the file
                        b2Key = java.net.URLDecoder.decode(b2Key, StandardCharsets.UTF_8);
                        b2Client.deleteFileVersion(b2Key, file.getFileId());
                    } catch (Exception e) {
                        log.warn("Failed to delete from B2: {} due to {}", b2Key, e.getMessage());
                        // Continue to delete from DB
                    }
                } else {
                    log.warn("No B2 fileId for {}, skipping storage deletion.", file.getFileName());
                }

                uploadedFileRepository.delete(file);
                return true;
            } catch (Exception e) {
                log.error("Failed to delete file: {}", fileUrl, e);
                return false;
            }
        }
        return false;
    }

    /**
     * Delete an entire folder/prefix
     */
    public void deleteFolder(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            return;
        }
        // Ensure folder path ends with slash if not already, to match prefix behavior
        String prefix = folderPath.endsWith("/") ? folderPath : folderPath + "/";
        b2Client.deleteFilesWithPrefix(prefix);
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
        String tempFolder;
        if (folder != null && folder.startsWith("temp/")) {
            tempFolder = folder;
        } else if (folder != null && folder.startsWith("cars/")) {
            String carId = extractCarIdFromFolder(folder);
            if (carId != null && !carId.isBlank()) {
                tempFolder = "temp/cars/" + carId + "/images";
            } else {
                tempFolder = "temp/cars";
            }
        } else if (folder != null && folder.startsWith("chat/")) {
            tempFolder = "temp/chat";
        } else {
            tempFolder = "temp/" + uploader.getId();
        }

        String uniqueFileName = generateUniqueFileName(originalFileName, uploader.getId());
        String fullPath = tempFolder + "/" + uniqueFileName;

        // B2 requires the file name to be URL-encoded for the upload request header if
        // it contains special chars
        String b2FileName = URLEncoder.encode(fullPath, StandardCharsets.UTF_8).replace("+", "%20");

        // 1. Get One-Time Upload URL from B2
        B2Client.GetUploadUrlResponse uploadUrl = b2Client.getUploadUrl();

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
        if (fileHash != null && !fileHash.equals("none")) {
            Optional<UploadedFile> existing = uploadedFileRepository.findByFileHashAndUploadedById(fileHash,
                    uploader.getId());
            if (existing.isPresent()) {
                log.info("Duplicate file detected for user {}: {}", uploader.getId(), fileHash);
                // Delete temp copy
                try {
                    b2Client.deleteFileVersion(b2FileName, fileId);
                } catch (Exception e) {
                    log.error("Failed to delete duplicate temp file from B2: {}", b2FileName, e);
                }
                return existing.get();
            }
        }

        // 3. Save as TemporaryFile or UploadedFile based on path
        String decodedPath = java.net.URLDecoder.decode(b2FileName, StandardCharsets.UTF_8);
        String fileName = decodedPath.contains("/") ? decodedPath.substring(decodedPath.lastIndexOf("/") + 1)
                : decodedPath;

        String domain = properties.getCdnDomain();
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        String publicUrl = domain + "/" + decodedPath;

        // DIRECT FINALIZATION: If path is not "temp/", save directly as UploadedFile
        if (!decodedPath.startsWith("temp/")) {
            UploadedFile finalFile = UploadedFile.builder()
                    .fileUrl(publicUrl)
                    .fileName(fileName)
                    .originalFileName(originalFileName != null ? originalFileName : fileName)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .size(fileSize != null ? fileSize : 0L)
                    .uploadedBy(uploader)
                    .ownerType(ownerType)
                    .ownerId(ownerId)
                    .accessType(AccessType.PUBLIC) // Default to Public for direct uploads
                    .fileHash(fileHash)
                    .fileId(fileId)
                    .build();

            log.info("Directly finalized upload: {}", publicUrl);
            return uploadedFileRepository.save(finalFile);
        }

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

    private String extractCarIdFromFolder(String folder) {
        String path = folder;
        if (path.startsWith("cars/")) {
            path = path.substring("cars/".length());
        }
        int slashIndex = path.indexOf('/');
        if (slashIndex != -1) {
            return path.substring(0, slashIndex);
        }
        return path.isEmpty() ? null : path;
    }
}
