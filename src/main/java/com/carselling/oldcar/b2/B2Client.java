package com.carselling.oldcar.b2;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class B2Client {

    private final B2AuthService authService;
    private final B2Properties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

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
        try {
            // 1. Get Authentication
            B2AuthService.B2AuthResponse auth = authService.getAuth();

            // 2. Get Upload URL
            GetUploadUrlResponse uploadUrlResponse = getUploadUrl(auth, properties.getBucketId());

            // 3. Upload File
            String sha1 = calculateSha1(content);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrlResponse.getUploadUrl()))
                    .header("Authorization", uploadUrlResponse.getAuthorizationToken())
                    .header("X-Bz-File-Name", fileName) // URL encoding handled by caller or simple allowed chars? B2
                                                        // requires URI encoding.
                    .header("Content-Type", contentType)
                    .header("X-Bz-Content-Sha1", sha1)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to upload file to B2: Status={}, Body={}", response.statusCode(), response.body());
                throw new RuntimeException("B2 Upload failed: " + response.body());
            }

            return objectMapper.readValue(response.body(), UploadFileResponse.class);

        } catch (Exception e) {
            log.error("Error in B2 upload", e);
            throw new RuntimeException("B2 Upload Error", e);
        }
    }

    // Deletion support
    public void deleteFileVersion(String fileName, String fileId) {
        try {
            B2AuthService.B2AuthResponse auth = authService.getAuth();

            Map<String, String> payload = Map.of(
                    "fileName", fileName,
                    "fileId", fileId);

            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(auth.getApiUrl() + "/b2api/v2/b2_delete_file_version"))
                    .header("Authorization", auth.getAuthorizationToken())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Failed to delete file version from B2: {}", response.body());
                // Don't throw hard exception for delete failure
            } else {
                log.info("Deleted file version {} from B2", fileName);
            }

        } catch (Exception e) {
            log.error("Error deleting file from B2", e);
        }
    }

    public GetUploadUrlResponse getUploadUrl() {
        try {
            B2AuthService.B2AuthResponse auth = authService.getAuth();
            return getUploadUrl(auth, properties.getBucketId());
        } catch (Exception e) {
            log.error("Error getting upload URL", e);
            throw new RuntimeException("Failed to get B2 upload URL", e);
        }
    }

    private GetUploadUrlResponse getUploadUrl(B2AuthService.B2AuthResponse auth, String bucketId)
            throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of("bucketId", bucketId));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(auth.getApiUrl() + "/b2api/v2/b2_get_upload_url"))
                .header("Authorization", auth.getAuthorizationToken())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get B2 upload URL: " + response.body());
        }

        return objectMapper.readValue(response.body(), GetUploadUrlResponse.class);
    }

    private String calculateSha1(byte[] content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-1 calculation failed", e);
        }
    }
}
