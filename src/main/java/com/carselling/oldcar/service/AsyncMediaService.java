package com.carselling.oldcar.service;

import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.MediaStatus;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.b2.B2FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncMediaService {

    private final CarRepository carRepository;
    private final TransactionTemplate transactionTemplate;
    // Assuming B2FileService can provide public URLs
    private final B2FileService b2FileService;

    @Async
    public void processMedia(Long carId, List<String> fileUrls) {
        log.info("Starting async media processing for car: {}", carId);

        try {
            // Step 1: Set Status PROCESSING
            updateStatus(carId, MediaStatus.PROCESSING);

            // Step 2: Validate Files exist (and potentially scan them)
            // For now, we assume they are valid B2 URLs or paths.
            // In a real scenario, we would download, scan (ClamAV), and verify dimensions.
            // Here we just verify reasonable URL format.

            List<String> validImageUrls = new ArrayList<>();
            String videoUrl = null;

            for (String url : fileUrls) {
                if (url == null || url.isBlank())
                    continue;

                if (isSuspicious(url)) {
                    log.warn("Suspicious file detected for car {}: {}", carId, url);
                    continue; // Skip suspicious files
                }

                // Basic extension check
                String lowerUrl = url.toLowerCase();
                if (lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".mov") || lowerUrl.endsWith(".avi")) {
                    videoUrl = url;
                } else {
                    validImageUrls.add(url);
                }
            }

            // Step 3: Update Car with Validated Media
            updateCarMedia(carId, validImageUrls, videoUrl);

            log.info("Media processing completed for car: {}", carId);

        } catch (Exception e) {
            log.error("Media processing failed for car: {}", carId, e);
            updateStatus(carId, MediaStatus.FAILED);
        }
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

    private void updateCarMedia(Long carId, List<String> imageUrls, String videoUrl) {
        transactionTemplate.execute(tx -> {
            Car car = carRepository.findById(carId).orElse(null);
            if (car == null)
                return null;

            if (!imageUrls.isEmpty()) {
                car.setImages(imageUrls);
                // Set first image as banner if not set
                if (car.getImageUrl() == null || car.getImageUrl().isBlank()) {
                    car.setImageUrl(imageUrls.get(0));
                }
            }

            if (videoUrl != null) {
                car.setVideoUrl(videoUrl);
            }

            car.setMediaStatus(MediaStatus.READY);

            // Auto-activate if verified dealer
            if (car.getOwner().canListCarsPublicly()) {
                car.setIsActive(true);
                car.setIsAvailable(true);
            }

            carRepository.save(car);
            return null;
        });
    }

    private boolean isSuspicious(String url) {
        // Simple security check
        return url.contains("..") || url.contains(".exe") || url.contains(".sh");
    }
}
