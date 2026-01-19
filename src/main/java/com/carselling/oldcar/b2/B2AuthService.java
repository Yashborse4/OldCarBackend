package com.carselling.oldcar.b2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class B2AuthService {

    private final B2Properties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private volatile B2AuthResponse cachedAuth;
    private LocalDateTime validUntil;

    @Data
    public static class B2AuthResponse {
        private String accountId;
        @JsonProperty("authorizationToken")
        private String authorizationToken;
        @JsonProperty("apiUrl")
        private String apiUrl;
        @JsonProperty("downloadUrl")
        private String downloadUrl;
        private long recommendedPartSize;
        private long absoluteMinimumPartSize;
    }

    public synchronized B2AuthResponse getAuth() {
        if (cachedAuth != null && LocalDateTime.now().isBefore(validUntil)) {
            return cachedAuth;
        }
        return authorize();
    }

    private B2AuthResponse authorize() {
        try {
            log.info("Authorizing with Backblaze B2...");
            String authHeader = "Basic " + Base64.getEncoder().encodeToString(
                    (properties.getApplicationKeyId() + ":" + properties.getApplicationKey())
                            .getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.backblazeb2.com/b2api/v2/b2_authorize_account"))
                    .header("Authorization", authHeader)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to authorize with B2: " + response.body());
            }

            B2AuthResponse authResponse = objectMapper.readValue(response.body(), B2AuthResponse.class);
            cachedAuth = authResponse;
            // Token valid for 24h, refresh comfortably before (e.g., 23h)
            validUntil = LocalDateTime.now().plusHours(23);

            log.info("Successfully authorized with Backblaze B2. API URL: {}", authResponse.getApiUrl());
            return authResponse;
        } catch (Exception e) {
            log.error("B2 Authorization failed", e);
            throw new RuntimeException("B2 Authorization failed", e);
        }
    }
}
