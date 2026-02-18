package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.dto.car.MediaUploadRequest;
import com.carselling.oldcar.dto.car.UpdateMediaStatusRequest;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.model.UploadedFile;
import com.carselling.oldcar.service.car.CarService;
import com.carselling.oldcar.service.TempFileStorageService;
import com.carselling.oldcar.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Controller for Car Media Management (Uploads, Status)
 */
@RestController
@RequestMapping("/api/cars")
@RequiredArgsConstructor
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "Car Media", description = "Vehicle media management")
public class CarMediaController {

        private final CarService carService;
        private final TempFileStorageService tempFileStorageService;
        private final com.carselling.oldcar.repository.UserRepository userRepository;

        /**
         * Update Vehicle Media Status
         * POST /api/cars/{id}/media-status
         */
        @PostMapping("/{id}/media-status")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Update media status", description = "Update processing status of vehicle media")
        public ResponseEntity<ApiResponse<CarResponse>> updateVehicleMediaStatus(
                        @PathVariable String id,
                        @Valid @RequestBody UpdateMediaStatusRequest statusRequest) {

                log.info("Updating vehicle media status: {} to {}", id, statusRequest.getStatus());

                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponse updatedCar = carService.updateMediaStatus(id, statusRequest.getStatus(), currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle media status updated successfully",
                                String.format("Vehicle media status changed to %s", statusRequest.getStatus()),
                                updatedCar));
        }

        /**
         * Upload Vehicle Media (Direct-to-storage URLs)
         * POST /api/cars/{id}/media
         */
        @PostMapping("/{id}/media")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Upload media URLs", description = "Link direct upload URLs to vehicle")
        public ResponseEntity<ApiResponse<CarResponse>> uploadVehicleMedia(
                        @PathVariable String id,
                        @RequestBody MediaUploadRequest uploadRequest) {

                log.info("Uploading vehicle media for: {} (images: {}, video: {})",
                                id,
                                uploadRequest.getImages() != null ? uploadRequest.getImages().size() : 0,
                                uploadRequest.getVideoUrl() != null ? "Yes" : "No");

                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponse updatedCar = carService.uploadMedia(
                                id,
                                uploadRequest.getImages(),
                                uploadRequest.getVideoUrl(),
                                currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle media updated successfully",
                                "Media files have been linked to your vehicle",
                                updatedCar));
        }

        /**
         * Upload Temp File (Secure Upload Flow)
         * POST /api/cars/{id}/media/temp-upload
         */
        @PostMapping("/{id}/media/temp-upload")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Upload temp file", description = "Upload file to temporary storage with validation")
        public ResponseEntity<ApiResponse<UploadedFile>> uploadTempFile(
                        @PathVariable String id,
                        @RequestParam("file") MultipartFile file) {

                log.info("Uploading temp file for car: {} (file: {})", id, file.getOriginalFilename());

                try {
                        Long currentUserId = SecurityUtils.getCurrentUserId();
                        com.carselling.oldcar.model.User currentUser = userRepository.findById(currentUserId)
                                        .orElseThrow(() -> new com.carselling.oldcar.exception.ResourceNotFoundException(
                                                        "User", "id", currentUserId.toString()));

                        // Store temp file with validation
                        UploadedFile tempFile = tempFileStorageService.storeTempFile(
                                        file,
                                        currentUser,
                                        com.carselling.oldcar.model.ResourceType.CAR_IMAGE,
                                        Long.parseLong(id));

                        return ResponseEntity.ok(ApiResponse.success(
                                        "Temp file uploaded successfully",
                                        "File has been stored in temporary storage with validation",
                                        tempFile));

                } catch (IllegalArgumentException e) {
                        log.error("File validation failed: {}", e.getMessage());
                        return ResponseEntity.badRequest().body(ApiResponse.error(
                                        "File validation failed",
                                        e.getMessage()));
                } catch (IOException e) {
                        log.error("Error storing temp file", e);
                        return ResponseEntity.internalServerError().body(ApiResponse.error(
                                        "Error storing file",
                                        "Failed to store file in temporary storage"));
                }
        }

        /**
         * Finalize Vehicle Media (Secure Upload Flow)
         * POST /api/cars/{id}/media/finalize
         */
        @PostMapping("/{id}/media/finalize")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Finalize uploaded media", description = "Finalize temporary uploads for vehicle")
        public ResponseEntity<ApiResponse<CarResponse>> finalizeVehicleMedia(
                        @PathVariable String id,
                        @RequestBody List<Long> tempFileIds) {

                log.info("Finalizing vehicle media for: {} (files: {})", id, tempFileIds.size());

                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponse updatedCar = carService.finalizeMedia(id, tempFileIds, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Vehicle media finalized successfully",
                                "Media files have been securely processed and linked",
                                updatedCar));
        }

        // ==================== GRANULAR MEDIA MANAGEMENT ====================

        /**
         * Delete a specific image by index
         * DELETE /api/cars/{id}/media/images/{index}
         */
        @DeleteMapping("/{id}/media/images/{index}")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Delete image", description = "Delete a specific image by index")
        public ResponseEntity<ApiResponse<CarResponse>> deleteImage(
                        @PathVariable String id,
                        @PathVariable int index) {

                log.info("Deleting image at index {} from car {}", index, id);
                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponse updatedCar = carService.deleteCarImage(id, index, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Image deleted successfully",
                                "Image has been removed from your vehicle",
                                updatedCar));
        }

        /**
         * Replace a specific image by index (new image URL in body)
         * PUT /api/cars/{id}/media/images/{index}
         */
        @PutMapping("/{id}/media/images/{index}")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Replace image", description = "Replace a specific image by index with a new URL")
        public ResponseEntity<ApiResponse<CarResponse>> replaceImage(
                        @PathVariable String id,
                        @PathVariable int index,
                        @RequestBody java.util.Map<String, String> body) {

                String newImageUrl = body.get("imageUrl");
                if (newImageUrl == null || newImageUrl.isBlank()) {
                        return ResponseEntity.badRequest().body(ApiResponse.error(
                                        "Missing imageUrl",
                                        "Request body must contain 'imageUrl' field"));
                }

                log.info("Replacing image at index {} for car {}", index, id);
                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponse updatedCar = carService.replaceCarImage(id, index, newImageUrl, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Image replaced successfully",
                                "Image has been updated on your vehicle",
                                updatedCar));
        }

        /**
         * Delete the video from a car
         * DELETE /api/cars/{id}/media/video
         */
        @DeleteMapping("/{id}/media/video")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Delete video", description = "Delete the video from a vehicle")
        public ResponseEntity<ApiResponse<CarResponse>> deleteVideo(
                        @PathVariable String id) {

                log.info("Deleting video from car {}", id);
                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponse updatedCar = carService.deleteCarVideo(id, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Video deleted successfully",
                                "Video has been removed from your vehicle",
                                updatedCar));
        }

        /**
         * Set an image as the banner (cover) by index
         * PUT /api/cars/{id}/media/banner/{index}
         */
        @PutMapping("/{id}/media/banner/{index}")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
        @io.swagger.v3.oas.annotations.Operation(summary = "Set banner image", description = "Set an image as the banner/cover by index")
        public ResponseEntity<ApiResponse<CarResponse>> setBanner(
                        @PathVariable String id,
                        @PathVariable int index) {

                log.info("Setting image at index {} as banner for car {}", index, id);
                Long currentUserId = SecurityUtils.getCurrentUserId();
                CarResponse updatedCar = carService.updateCarBanner(id, index, currentUserId);

                return ResponseEntity.ok(ApiResponse.success(
                                "Banner updated successfully",
                                "Cover image has been changed",
                                updatedCar));
        }
}
