
package com.carselling.oldcar.b2;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.B2StorageClientFactory;
import org.springframework.beans.factory.InitializingBean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.backblaze.b2.client.structures.B2FileVersion;
import com.backblaze.b2.client.structures.B2ListFileVersionsRequest;

import com.backblaze.b2.client.exceptions.B2Exception;

@Component
@RequiredArgsConstructor
public class B2Client implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(B2Client.class);

    private final B2Properties properties;
    private B2StorageClient client;
    private String cachedBucketId;

    @Override
    public void afterPropertiesSet() {
        try {
            log.info("Initializing B2 SDK Client...");
            client = B2StorageClientFactory
                    .createDefaultFactory()
                    .create(properties.getApplicationKeyId(), properties.getApplicationKey(), "OldCarApp/1.0");
            log.info("B2 SDK Client initialized successfully");

            // Pre-cache bucket ID to avoid ~3s resolution delay on every upload
            this.cachedBucketId = resolveBucketIdInternal(properties.getBucketId());
            log.info("Cached B2 bucket ID: {}", cachedBucketId);
        } catch (Exception e) {
            log.error("Failed to initialize B2 SDK Client", e);
            throw new RuntimeException("B2 Initialization Failed", e);
        }
    }

    @Data
    public static class GetUploadUrlResponse {
        private String bucketId;
        private String uploadUrl;
        private String authorizationToken;
    }

    @Data
    public static class UploadFileResponse {
        private String fileId;
        private String fileName;
        private String accountId;
        private String bucketId;
        private long contentLength;
        private String contentSha1;
        private String contentType;
        private Map<String, String> fileInfo;
    }

    public UploadFileResponse uploadFile(String fileName, byte[] content, String contentType) throws B2Exception {
        return uploadFile(fileName, content, contentType, Map.of());
    }

    /**
     * Upload file with custom metadata (ownership tagging).
     */
    public UploadFileResponse uploadFile(String fileName, byte[] content, String contentType,
            Map<String, String> fileInfo) throws B2Exception {
        try {
            String bucketId = getCachedBucketId();

            com.backblaze.b2.client.contentSources.B2ContentSource source = com.backblaze.b2.client.contentSources.B2ByteArrayContentSource
                    .build(content);

            var requestBuilder = com.backblaze.b2.client.structures.B2UploadFileRequest
                    .builder(bucketId, fileName, contentType, source);

            // Add custom file info (metadata)
            if (fileInfo != null && !fileInfo.isEmpty()) {
                for (Map.Entry<String, String> entry : fileInfo.entrySet()) {
                    requestBuilder.setCustomField(entry.getKey(), entry.getValue());
                }
            }

            com.backblaze.b2.client.structures.B2UploadFileRequest request = requestBuilder.build();
            com.backblaze.b2.client.structures.B2FileVersion fileVersion = client.uploadSmallFile(request);

            UploadFileResponse response = new UploadFileResponse();
            response.setFileId(fileVersion.getFileId());
            response.setFileName(fileName);
            response.setBucketId(bucketId);
            response.setContentLength(content.length);
            response.setContentType(contentType);
            response.setFileInfo(fileInfo);

            return response;

        } catch (B2Exception e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in B2 upload (SDK)", e);
            throw new RuntimeException("B2 Upload Error", e);
        }
    }

    public UploadFileResponse uploadFile(String fileName, java.io.File file, String contentType,
            Map<String, String> fileInfo) throws B2Exception {
        try {
            String bucketId = getCachedBucketId();

            com.backblaze.b2.client.contentSources.B2ContentSource source = com.backblaze.b2.client.contentSources.B2FileContentSource
                    .build(file);

            var requestBuilder = com.backblaze.b2.client.structures.B2UploadFileRequest
                    .builder(bucketId, fileName, contentType, source);

            if (fileInfo != null && !fileInfo.isEmpty()) {
                for (Map.Entry<String, String> entry : fileInfo.entrySet()) {
                    requestBuilder.setCustomField(entry.getKey(), entry.getValue());
                }
            }

            com.backblaze.b2.client.structures.B2UploadFileRequest request = requestBuilder.build();
            com.backblaze.b2.client.structures.B2FileVersion fileVersion = client.uploadSmallFile(request);

            UploadFileResponse response = new UploadFileResponse();
            response.setFileId(fileVersion.getFileId());
            response.setFileName(fileName);
            response.setBucketId(bucketId);
            response.setContentLength(file.length());
            response.setContentType(contentType);
            response.setFileInfo(fileInfo);

            return response;
        } catch (B2Exception e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in B2 file upload", e);
            throw new RuntimeException("B2 Upload Error", e);
        }
    }

    // Deletion support
    public void deleteFileVersion(String fileName, String fileId) {
        try {
            client.deleteFileVersion(fileName, fileId);
            log.info("Deleted file version {} from B2", fileName);
        } catch (B2Exception e) {
            if ("file_not_present".equals(e.getCode())) {
                log.warn("File version {} not found in B2, skipping deletion.", fileName);
            } else {
                log.error("Error deleting file from B2: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Error deleting file from B2", e);
        }
    }

    /**
     * Delete a file by its public URL (extracts key and deletes all versions).
     * Useful when we don't have the fileId from the database.
     */
    public void deleteFileByUrl(String fileUrl) {
        try {
            String domain = properties.getCdnDomain();
            String b2Key = fileUrl.replace(domain + "/", "");
            if (b2Key.startsWith("/")) {
                b2Key = b2Key.substring(1);
            }
            // Decode in case of URL encoding
            final String fileName = java.net.URLDecoder.decode(b2Key, java.nio.charset.StandardCharsets.UTF_8);

            log.info("Attempting to delete file by URL: {} -> Key: {}", fileUrl, fileName);

            String bucketId = getCachedBucketId();
            // List versions with this name and delete them
            B2ListFileVersionsRequest request = B2ListFileVersionsRequest
                    .builder(bucketId)
                    .setPrefix(fileName) // optimization: prefix match
                    .build();

            boolean found = false;
            for (B2FileVersion version : client.fileVersions(request)) {
                if (version.getFileName().equals(fileName)) {
                    try {
                        client.deleteFileVersion(version.getFileName(), version.getFileId());
                        log.info("Deleted file version: {} ({})", version.getFileName(), version.getFileId());
                        found = true;
                    } catch (Exception e) {
                        log.warn("Failed to delete file version: {}", version.getFileName(), e);
                    }
                }
            }

            if (!found) {
                log.warn("No file versions found in B2 for key: {}", fileName);
            }

        } catch (Exception e) {
            log.error("Failed to delete file by URL: {}", fileUrl, e);
        }
    }

    public void deleteFilesWithPrefix(String prefix) {
        try {
            String bucketId = getCachedBucketId();
            log.info("Deleting files with prefix: {}", prefix);

            // Create request with prefix for server-side filtering
            B2ListFileVersionsRequest request = B2ListFileVersionsRequest
                    .builder(bucketId)
                    .setPrefix(prefix)
                    .build();

            // Iterate over file versions matching the prefix
            for (B2FileVersion version : client.fileVersions(request)) {
                try {
                    client.deleteFileVersion(version.getFileName(), version.getFileId());
                    log.debug("Deleted file version: {} ({})", version.getFileName(), version.getFileId());
                } catch (Exception e) {
                    log.warn("Failed to delete file version: {}", version.getFileName(), e);
                }
            }
            log.info("Finished deleting files with prefix: {}", prefix);
        } catch (Exception e) {
            log.error("Error deleting files with prefix: {}", prefix, e);
            throw new RuntimeException("Failed to delete folder/prefix in B2", e);
        }
    }

    public java.util.List<B2FileVersion> listFiles(String prefix) {
        try {
            String bucketId = getCachedBucketId();
            B2ListFileVersionsRequest request = B2ListFileVersionsRequest
                    .builder(bucketId)
                    .setPrefix(prefix)
                    .build();

            java.util.List<B2FileVersion> files = new java.util.ArrayList<>();
            for (B2FileVersion version : client.fileVersions(request)) {
                // We only care about "upload" actions (existing files), not "hide" markers if
                // any
                if (version.isUpload()) {
                    files.add(version);
                }
            }
            return files;
        } catch (Exception e) {
            log.error("Error listing files with prefix: {}", prefix, e);
            throw new RuntimeException("Failed to list files in B2", e);
        }
    }

    @Data
    public static class ListBucketsResponse {
        private java.util.List<Bucket> buckets;
    }

    @Data
    public static class Bucket {
        private String bucketId;
        private String bucketName;
        private String bucketType;
    }

    private GetUploadUrlResponse cachedUploadUrlResponse;
    private long cachedUploadUrlTime;

    public synchronized GetUploadUrlResponse getUploadUrl() {
        // Return cached response if valid (token lasts 24h, we cache for 23h to be
        // safe)
        if (cachedUploadUrlResponse != null && (System.currentTimeMillis() - cachedUploadUrlTime) < 23 * 3600 * 1000L) {
            return cachedUploadUrlResponse;
        }

        try {
            String bucketId = getCachedBucketId();

            com.backblaze.b2.client.structures.B2GetUploadUrlRequest request = com.backblaze.b2.client.structures.B2GetUploadUrlRequest
                    .builder(bucketId).build();

            com.backblaze.b2.client.structures.B2UploadUrlResponse sdkResponse = client.getUploadUrl(request);

            GetUploadUrlResponse response = new GetUploadUrlResponse();
            response.setBucketId(bucketId);
            response.setUploadUrl(sdkResponse.getUploadUrl());
            response.setAuthorizationToken(sdkResponse.getAuthorizationToken());

            this.cachedUploadUrlResponse = response;
            this.cachedUploadUrlTime = System.currentTimeMillis();
            log.info("Refreshed and cached B2 Upload URL");

            return response;
        } catch (Exception e) {
            log.error("Error getting upload URL (SDK)", e);
            throw new RuntimeException("Failed to get B2 upload URL", e);
        }
    }

    /**
     * Get cached bucket ID, resolving it if necessary (fallback for edge cases)
     */
    private String getCachedBucketId() {
        if (cachedBucketId != null) {
            return cachedBucketId;
        }
        // Fallback: resolve and cache if not initialized
        log.warn("Bucket ID was not cached, resolving now...");
        this.cachedBucketId = resolveBucketIdInternal(properties.getBucketId());
        return cachedBucketId;
    }

    @Data
    public static class B2FileInfo {
        private String fileId;
        private String fileName;
        private String contentSha1;
        private Long contentLength;
        private String contentType;
        private Map<String, String> fileInfo;
    }

    public B2FileInfo getFileInfo(String fileId) {
        try {
            com.backblaze.b2.client.structures.B2FileVersion fileVersion = client.getFileInfo(fileId);

            B2FileInfo info = new B2FileInfo();
            info.setFileId(fileVersion.getFileId());
            info.setFileName(fileVersion.getFileName());
            info.setFileName(fileVersion.getFileName());
            info.setContentSha1(fileVersion.getContentSha1());
            info.setContentLength(fileVersion.getContentLength());
            info.setContentType(fileVersion.getContentType());
            info.setFileInfo(fileVersion.getFileInfo());

            return info;
        } catch (Exception e) {
            log.error("Error getting file info from B2", e);
            throw new RuntimeException("Failed to get file info", e);
        }
    }

    /**
     * Move file by downloading from source and re-uploading to destination.
     * Backblaze B2 does not support a native "move" or server-side copy,
     * so we download → re-upload → (caller deletes original).
     *
     * @param sourceFileId   The B2 file ID of the source file
     * @param targetFileName The full B2 key/path for the destination file
     * @return The new B2 file ID of the re-uploaded file at the target location
     */
    public String copyFile(String sourceFileId, String targetFileName) throws B2Exception {
        log.info("Starting file move (Download -> Upload) for sourceId: {} to target: {}", sourceFileId,
                targetFileName);
        try {
            // 1. Get Source Info to preserve Content-Type
            com.backblaze.b2.client.structures.B2FileVersion sourceVersion = client.getFileInfo(sourceFileId);
            String sourceFileName = sourceVersion.getFileName();

            // Use B2's native content type (set during original upload)
            String contentType = sourceVersion.getContentType();
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            // 2. Download Content (Source)
            com.backblaze.b2.client.structures.B2DownloadByIdRequest downloadById = com.backblaze.b2.client.structures.B2DownloadByIdRequest
                    .builder(sourceFileId).build();

            // Buffer content in memory (file size < 200MB as per validation)
            final byte[][] contentHolder = new byte[1][];

            client.downloadById(downloadById, (response, inputStream) -> {
                try {
                    contentHolder[0] = inputStream.readAllBytes();
                } catch (java.io.IOException e) {
                    throw new RuntimeException("Failed to read download stream", e);
                }
            });

            if (contentHolder[0] == null || contentHolder[0].length == 0) {
                throw new RuntimeException("Download resulted in empty content for file: " + sourceFileName);
            }

            // 3. Upload to Destination (Target) and capture new file ID
            UploadFileResponse uploadResponse = uploadFile(targetFileName, contentHolder[0], contentType);
            String newFileId = uploadResponse.getFileId();

            log.info("Successfully moved (copied) file: {} -> {} (newFileId: {})",
                    sourceFileName, targetFileName, newFileId);

            return newFileId;

        } catch (B2Exception e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to move/copy file {} to {}: {}", sourceFileId, targetFileName, e.getMessage());
            throw new RuntimeException("Media move failed: " + e.getMessage(), e);
        }
    }

    /**
     * Ensures that the "folder" (prefix) exists or is writable.
     * In B2, folders are virtual, so we just verify bucket access and log the
     * intent.
     */
    public void ensureFolderExists(String prefix) {
        try {
            String bucketId = getCachedBucketId();
            // Perform a lightweight check (e.g., list 1 file) to ensure connectivity and
            // bucket existence
            // This satisfies the "create directory if not exists" requirement by ensuring
            // the path is valid for writing.
            B2ListFileVersionsRequest request = B2ListFileVersionsRequest
                    .builder(bucketId)
                    .setPrefix(prefix)
                    .setMaxFileCount(1)
                    .build();
            client.fileVersions(request).iterator().hasNext(); // Just trigger the call
            log.info("Storage path verified: {}", prefix);
        } catch (Exception e) {
            log.error("Failed to verify storage path: {}", prefix, e);
            throw new RuntimeException("Storage unavailable for path: " + prefix, e);
        }
    }

    /**
     * Check if a file exists in B2
     */
    public boolean fileExists(String fileName) {
        try {
            String bucketId = getCachedBucketId();
            // Prefix search with max count 1 to check existence efficiently
            B2ListFileVersionsRequest request = B2ListFileVersionsRequest
                    .builder(bucketId)
                    .setPrefix(fileName)
                    .setMaxFileCount(1)
                    .build();

            for (B2FileVersion version : client.fileVersions(request)) {
                if (version.getFileName().equals(fileName)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Error checking file existence: {}", fileName, e);
            return false; // Fail safe
        }
    }

    /**
     * Internal method to resolve bucket ID from name or ID string
     */
    private String resolveBucketIdInternal(String bucketNameOrId) {
        // If it looks like a bucket ID (24 char hex), assume it is one
        if (bucketNameOrId.matches("^[a-fA-F0-9]{24}$")) {
            return bucketNameOrId;
        }

        log.info("Resolving Bucket ID for name: {}", bucketNameOrId);
        try {
            return client.getBucketOrNullByName(bucketNameOrId).getBucketId();
        } catch (Exception e) {
            log.error("Failed to resolve bucket ID for name: {}", bucketNameOrId, e);
            throw new RuntimeException("Bucket not found: " + bucketNameOrId, e);
        }
    }
}
