package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.file.FileMetadata;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.StorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
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
        Bucket bucket = StorageClient.getInstance(firebaseApp).bucket();
        if (bucket == null) {
            log.warn("Firebase storage bucket is not configured, returning null signed URL");
            return null;
        }

        Blob blob = bucket.get(fileName);
        if (blob == null) {
            log.warn("File not found in bucket for name: {}", fileName);
            return null;
        }

        URL url = blob.signUrl(expirationMinutes, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature());
        return url != null ? url.toString() : null;
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
        Long updateTime = blob.getUpdateTime();
        if (updateTime != null) {
            lastModified = LocalDateTime.ofInstant(Instant.ofEpochMilli(updateTime), ZoneId.systemDefault());
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
