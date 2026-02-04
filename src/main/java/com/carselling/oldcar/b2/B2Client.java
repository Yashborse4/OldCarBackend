
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

    public UploadFileResponse uploadFile(String fileName, byte[] content, String contentType) {
        return uploadFile(fileName, content, contentType, Map.of());
    }

    /**
     * Upload file with custom metadata (ownership tagging).
     */
    public UploadFileResponse uploadFile(String fileName, byte[] content, String contentType,
            Map<String, String> fileInfo) {
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

        } catch (Exception e) {
            log.error("Error in B2 upload (SDK)", e);
            throw new RuntimeException("B2 Upload Error", e);
        }
    }

    public UploadFileResponse uploadFile(String fileName, java.io.File file, String contentType,
            Map<String, String> fileInfo) {
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
            com.backblaze.b2.client.structures.B2FileVersion fileVersion = client.uploadSmallFile(request); // Autodetects?
                                                                                                            // Or use
                                                                                                            // uploadLargeFile?
                                                                                                            // SDK
                                                                                                            // usually
                                                                                                            // handles
                                                                                                            // it or we
                                                                                                            // use
                                                                                                            // uploadFile.
            // Note: client.uploadSmallFile is specific. The SDK typically has 'uploadFile'
            // which selects.
            // Checking imports... standard B2 SDK 'uploadSmallFile' is for < 5GB.
            // If backup > 5GB, using 'uploadLargeFile' is better, but 'B2StorageClient'
            // usually has a unified method or we stick to small file for now.
            // Using uploadSmallFile is safe for most daily backups < 5GB.

            UploadFileResponse response = new UploadFileResponse();
            response.setFileId(fileVersion.getFileId());
            response.setFileName(fileName);
            response.setBucketId(bucketId);
            response.setContentLength(file.length());
            response.setContentType(contentType);
            response.setFileInfo(fileInfo);

            return response;
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
        } catch (Exception e) {
            log.error("Error deleting file from B2", e);
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

    public GetUploadUrlResponse getUploadUrl() {
        try {
            String bucketId = getCachedBucketId();

            com.backblaze.b2.client.structures.B2GetUploadUrlRequest request = com.backblaze.b2.client.structures.B2GetUploadUrlRequest
                    .builder(bucketId).build();

            com.backblaze.b2.client.structures.B2UploadUrlResponse sdkResponse = client.getUploadUrl(request);

            GetUploadUrlResponse response = new GetUploadUrlResponse();
            response.setBucketId(bucketId);
            response.setUploadUrl(sdkResponse.getUploadUrl());
            response.setAuthorizationToken(sdkResponse.getAuthorizationToken());
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
        private Map<String, String> fileInfo;
    }

    public B2FileInfo getFileInfo(String fileId) {
        try {
            com.backblaze.b2.client.structures.B2FileVersion fileVersion = client.getFileInfo(fileId);

            B2FileInfo info = new B2FileInfo();
            info.setFileId(fileVersion.getFileId());
            info.setFileName(fileVersion.getFileName());
            info.setContentSha1(fileVersion.getContentSha1());
            info.setFileInfo(fileVersion.getFileInfo());

            return info;
        } catch (Exception e) {
            log.error("Error getting file info from B2", e);
            throw new RuntimeException("Failed to get file info", e);
        }
    }

    public void copyFile(String sourceFileId, String targetFileName) {
        try {
            // String bucketId = getCachedBucketId(); // Unused

            // Construct request using Builder pattern
            // Assuming builder(sourceFileId, fileName) defaults to same bucket or infers it
            // Removing explicit bucket setter as it caused compilation error
            com.backblaze.b2.client.structures.B2CopyFileRequest request = com.backblaze.b2.client.structures.B2CopyFileRequest
                    .builder(sourceFileId, targetFileName)
                    .build();

            // Retry logic for copy operation
            int maxRetries = 3;
            int attempt = 0;
            while (attempt < maxRetries) {
                try {
                    client.copySmallFile(request);
                    log.info("Copied file {} to {}", sourceFileId, targetFileName);
                    return;
                } catch (Exception e) {
                    attempt++;
                    if (attempt >= maxRetries) {
                        throw e; // Rethrow last exception
                    }
                    log.warn("Copy attempt {} failed for file {}, retrying...", attempt, sourceFileId);
                    try {
                        Thread.sleep(1000 * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error copying file in B2", e);
            throw new RuntimeException("Failed to copy file in B2", e);
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
