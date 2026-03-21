package com.carselling.oldcar.service.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Service to verify Google ID Tokens
 */
@Service
@Slf4j
public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;

    public GoogleAuthService(@Value("${app.google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();
        log.info("Initialized GoogleIdTokenVerifier with client ID: {}", clientId);
    }

    /**
     * Verify Google ID Token and return payload
     * @param idTokenString The raw ID token from frontend
     * @return Payload if valid, null otherwise
     */
    public Payload verifyToken(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                return idToken.getPayload();
            } else {
                log.warn("Invalid Google ID Token received");
                return null;
            }
        } catch (Exception e) {
            log.error("Error verifying Google ID Token: {}", e.getMessage());
            return null;
        }
    }
}
