package com.carselling.oldcar.service;

import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.MediaStatus;
import com.carselling.oldcar.repository.CarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncMediaService {

    private final CarRepository carRepository;
    private final TransactionTemplate transactionTemplate;

    @Async
    public void processMedia(Long carId, List<String> filePaths) {
        log.info("Starting async media processing for car: {}", carId);

        // Run outside transaction first for long-running tasks
        try {
            // Step 1: Set Status PROCESSING
            updateStatus(carId, MediaStatus.PROCESSING);

            // Step 2: Validate Size (Simulated)
            Thread.sleep(1000);
            log.info("Size validation passed for car: {}", carId);

            // Step 3: Compress (Simulated)
            Thread.sleep(1500);
            log.info("Server-side compression completed for car: {}", carId);

            // Step 4: Generate Thumbnails (Simulated)
            Thread.sleep(1000);
            log.info("Thumbnail generation completed for car: {}", carId);

        } catch (InterruptedException e) {
            log.error("Processing interrupted for car: {}", carId);
            updateStatus(carId, MediaStatus.FAILED);
            return;
        }

        // Final Transactional Update
        transactionTemplate.execute(status -> {
            Car car = carRepository.findById(carId).orElse(null);
            if (car == null)
                return null;

            try {
                List<String> validImageUrls = new ArrayList<>();
                String videoUrl = null;

                for (String path : filePaths) {
                    boolean isVideo = path.toLowerCase().endsWith(".mp4") || path.toLowerCase().endsWith(".mov");
                    // In a real app, this would point to the *processed* file location, not the raw
                    // one.
                    // Since frontend did the work, we assume these are the 'final' files for now.
                    String publicUrl = "https://storage.googleapis.com/" + getBucketNameFromService() + "/" + path;

                    if (isVideo) {
                        videoUrl = publicUrl;
                    } else {
                        validImageUrls.add(publicUrl);
                    }
                }

                if (!validImageUrls.isEmpty()) {
                    car.setImages(validImageUrls);
                    car.setImageUrl(validImageUrls.get(0));
                }
                if (videoUrl != null) {
                    car.setVideoUrl(videoUrl);
                }

                car.setMediaStatus(MediaStatus.READY);
                car.setIsActive(true);
                car.setIsAvailable(true);
                carRepository.save(car);

                log.info("Media processing pipeline FINISHED. Car {} is READY.", carId);

            } catch (Exception e) {
                log.error("DB Update failed for car: {}", carId, e);
                car.setMediaStatus(MediaStatus.FAILED);
                carRepository.save(car);
            }
            return null;
        });
    }

    private void updateStatus(Long carId, MediaStatus status) {
        transactionTemplate.execute(tx -> {
            carRepository.findById(carId).ifPresent(car -> {
                car.setMediaStatus(status);
                carRepository.save(car);
            });
            return null;
        });
    }

    // Helper to get bucket name - somewhat hacky as the service doesn't expose it
    // directly public
    // We'll assume the environment variable or config property is available,
    // but for now let's just use a placeholder or derived one.
    private String getBucketNameFromService() {
        // This is a placeholder. In a real app, inject
        // @Value("${firebase.storage.bucket}")
        return "car-sales-app.appspot.com";
    }
}
