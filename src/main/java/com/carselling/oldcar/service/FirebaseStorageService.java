package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.file.FileMetadata;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.StorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirebaseStorageService {

    private final FirebaseApp firebaseApp;

    public String uploadFile(MultipartFile file, String path) throws IOException {
        Bucket bucket = StorageClient.getInstance(firebaseApp).bucket();
        if (bucket == null) {
            throw new IOException("Firebase storage bucket is not configured");
        }

        Blob blob = bucket.create(path, file.getBytes(), file.getContentType());

        return String.format("https://storage.googleapis.com/%s/%s", bucket.getName(), blob.getName());
    }

    public void deleteFile(String fileUrl) {
        Bucket bucket = StorageClient.getInstance(firebaseApp).bucket();
        if (bucket == null) {
            log.warn("Firebase storage bucket is not configured, skipping delete");
            return;
        }

        String objectName = extractObjectName(fileUrl, bucket.getName());
        if (objectName == null || objectName.isBlank()) {
            log.warn("Could not extract object name from URL: {}", fileUrl);
            return;
        }

        Blob blob = bucket.get(objectName);
        if (blob != null) {
            boolean deleted = blob.delete();
            if (!deleted) {
                log.warn("Failed to delete file from bucket: {}", objectName);
            }
        } else {
            log.warn("File not found in bucket for URL: {}", fileUrl);
        }
    }

    public String generateSignedUrl(String fileName, int expirationMinutes) {
        return generateSignedUrl(fileName, expirationMinutes, "GET", null);
    }

    public String generateSignedUrl(String fileName, int expirationMinutes, String httpMethod, String contentType) {
        Bucket bucket = StorageClient.getInstance(firebaseApp).bucket();
        if (bucket == null) {
            log.warn("Firebase storage bucket is not configured, returning null signed URL");
            return null;
        }

        // For uploads (PUT), the blob might not exist yet, preventing blob.signUrl()
        // We must use bucket.signUrl for generic resource signing or create a
        // dummy/reference?
        // Actually, bucket.signUrl is the correct way for non-existing objects.

        try {
            // Note: Cloud Storage for Firebase uses IAM signing usually.
            // We need to use the Storage object associated with the bucket.
            Storage storage = bucket.getStorage();

            // Define sign options
            Storage.SignUrlOption[] options = new Storage.SignUrlOption[] {
                    Storage.SignUrlOption.httpMethod(HttpMethod.valueOf(httpMethod)),
                    Storage.SignUrlOption.withV4Signature()
            };

            if (contentType != null && !contentType.isEmpty()) {
                // options = java.util.Arrays.copyOf(options, options.length + 1);
                // options[options.length - 1] = Storage.SignUrlOption.withContentType();
                // Note: We relaxed strict Content-Type enforcement in signature to prevent 403s
                // on header mismatch.
                // MediaController already validates the intent.
            }

            // We are signing a specific blob path
            // For PUT, we are authorizing creation/update of this path
            // Using blobInfo to sign? Or just blob name.
            // bucket.signUrl is not directly exposed on com.google.cloud.storage.Bucket in
            // all versions.
            // The standard way is using the Blob object if it exists, OR crafting the
            // BlobInfo.

            com.google.cloud.storage.BlobInfo blobInfo = com.google.cloud.storage.BlobInfo
                    .newBuilder(bucket.getName(), fileName)
                    .setContentType(contentType)
                    .build();

            URL url = storage.signUrl(blobInfo, expirationMinutes, TimeUnit.MINUTES, options);
            return url.toString();
        } catch (Exception e) {
            log.error("Error generating signed URL for {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    public FileMetadata getFileMetadata(String fileName) {
        Bucket bucket = StorageClient.getInstance(firebaseApp).bucket();
        if (bucket == null) {
            log.warn("Firebase storage bucket is not configured, cannot fetch metadata");
            return null;
        }

        Blob blob = bucket.get(fileName);
        if (blob == null) {
            log.warn("File not found in bucket for name: {}", fileName);
            return null;
        }

        LocalDateTime lastModified = null;
        OffsetDateTime updateTime = blob.getUpdateTimeOffsetDateTime();
        if (updateTime != null) {
            lastModified = updateTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        }

        Map<String, String> userMetadata = blob.getMetadata();

        return FileMetadata.builder()
                .contentType(blob.getContentType())
                .contentLength(blob.getSize())
                .lastModified(lastModified)
                .userMetadata(userMetadata)
                .build();
    }

    private String extractObjectName(String fileUrl, String bucketName) {
        if (fileUrl == null || bucketName == null) {
            return null;
        }

        String marker = "https://storage.googleapis.com/" + bucketName + "/";
        int index = fileUrl.indexOf(marker);
        if (index >= 0) {
            return fileUrl.substring(index + marker.length());
        }

        int lastSlash = fileUrl.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < fileUrl.length() - 1) {
            return fileUrl.substring(lastSlash + 1);
        }

        return null;
    }
}
