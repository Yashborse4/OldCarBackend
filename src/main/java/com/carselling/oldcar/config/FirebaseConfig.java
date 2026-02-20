package com.carselling.oldcar.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * Firebase configuration for push notifications.
 * Only loads when:
 * 1. app.firebase.enabled=true (property toggle)
 * 2. serviceAccountKey.json exists on the classpath
 */
@Configuration
@ConditionalOnProperty(name = "app.firebase.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class FirebaseConfig {

    @Value("${app.firebase.service-account-file:carwalanotfication-firebase-adminsdk-fbsvc-6ca72dc5fe.json}")
    private String serviceAccountFile;

    @Bean
    public FirebaseApp firebaseApp() {
        try {
            ClassPathResource resource = new ClassPathResource(serviceAccountFile);

            try (InputStream serviceAccount = resource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                if (FirebaseApp.getApps().isEmpty()) {
                    log.info("Initializing Firebase App...");
                    return FirebaseApp.initializeApp(options);
                } else {
                    return FirebaseApp.getInstance();
                }
            }
        } catch (IOException e) {
            log.error("Failed to initialize Firebase: {}", e.getMessage());
            return null;
        }
    }
}
