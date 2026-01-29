package com.carselling.oldcar.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp() {
        try {
            ClassPathResource resource = new ClassPathResource("serviceAccountKey.json");

            if (!resource.exists()) {
                log.warn("serviceAccountKey.json not found. Firebase features will be disabled.");
                return null;
            }

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
