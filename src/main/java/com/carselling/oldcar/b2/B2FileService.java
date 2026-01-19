package com.carselling.oldcar.b2;

import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.model.UploadedFile;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class B2FileService {

    private final B2Client b2Client;
    private final UploadedFileRepository uploadedFileRepository;
    private final B2Properties properties;

    public FileUploadResponse uploadFile(MultipartFile file, String folder, User uploader, ResourceType ownerType,
            Long ownerId) throws IOException {
        String originalFileName = file.getOriginalFilename();
        String uniqueFileName = generateUniqueFileName(originalFileName, uploader.getId());
        String fullPath = folder + "/" + uniqueFileName;

        // B2 requires the file name to be URL-encoded for the upload request header if
        // it contains special chars
        // But the path itself is the "file name" in B2 concept (including folders)
        String b2FileName = URLEncoder.encode(fullPath, StandardCharsets.UTF_8).replace("+", "%20"); // Simple encoding
                                                                                                     // match

        log.info("Uploading file to B2: {}", fullPath);

        b2Client.uploadFile(b2FileName, file.getBytes(),
                file.getContentType());

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

    public java.util.List<FileUploadResponse> uploadMultipleFiles(java.util.List<MultipartFile> files, String folder,
            User uploader, ResourceType ownerType, Long ownerId) {
        java.util.List<FileUploadResponse> responses = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            try {
                responses.add(uploadFile(file, folder, uploader, ownerType, ownerId));
            } catch (IOException e) {
                log.error("Failed to upload file: {}", file.getOriginalFilename(), e);
                // Return failed response indicator if needed, or structured error
                responses.add(FileUploadResponse.builder()
                        .originalFileName(file.getOriginalFilename())
                        .fileUrl(null)
                        .fileName("FAILED: " + e.getMessage())
                        .build()); // Using minimal fields for error
            }
        }
        return responses;
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

    public DirectUploadInitResponse initDirectUpload(String originalFileName, String folder, User uploader) {
        String uniqueFileName = generateUniqueFileName(originalFileName, uploader.getId());
        String fullPath = folder + "/" + uniqueFileName;

        // Ensure path is URL encoded for B2 if needed, but for "X-Bz-File-Name" header,
        // B2 expects percent-encoded UTF-8.
        // We will pass the RAW unique filename to the client, and the Client MUST send
        // it in X-Bz-File-Name header.
        String b2FileName = URLEncoder.encode(fullPath, StandardCharsets.UTF_8).replace("+", "%20");

        // 1. Get One-Time Upload URL from B2
        B2Client.GetUploadUrlResponse uploadUrl = b2Client.getUploadUrl();

        // 2. Construct future public URL
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

    public UploadedFile completeDirectUpload(String b2FileName, String fileId, User uploader, ResourceType ownerType,
            Long ownerId, Long fileSize, String originalFileName, String contentType) {
        // Decode filename to get original path if needed, but we stored it encoded-ish.
        // Actually we told client to use b2FileName. Client returns it.

        String decodedPath = java.net.URLDecoder.decode(b2FileName, StandardCharsets.UTF_8);
        // fullPath = folder/uniqueName

        String fileName = decodedPath.contains("/") ? decodedPath.substring(decodedPath.lastIndexOf("/") + 1)
                : decodedPath;

        String domain = properties.getCdnDomain();
        if (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        String publicUrl = domain + "/" + decodedPath;

        UploadedFile uploadedFile = UploadedFile.builder()
                .fileUrl(publicUrl)
                .fileName(fileName) // Unique name
                .originalFileName(originalFileName != null ? originalFileName : fileName) // Use provided or fallback
                .contentType(contentType != null ? contentType : "application/octet-stream") // Use provided or fallback
                .size(fileSize != null ? fileSize : 0L)
                .uploadedBy(uploader)
                .ownerType(ownerType)
                .ownerId(ownerId)
                .build();

        return uploadedFileRepository.save(uploadedFile);
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
