package com.carselling.oldcar.controller.car;

import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.dto.car.MediaUploadRequest;
import com.carselling.oldcar.dto.car.UpdateMediaStatusRequest;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.car.CarService;
import com.carselling.oldcar.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for Car Media Management (Uploads, Status)
 */
@RestController
@RequestMapping("/api/cars")
@RequiredArgsConstructor
@Slf4j
public class CarMediaController {

        private final CarService carService;

        /**
         * Update Vehicle Media Status
         * POST /api/cars/{id}/media-status
         */
        @PostMapping("/{id}/media-status")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
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
         * Finalize Vehicle Media (Secure Upload Flow)
         * POST /api/cars/{id}/media/finalize
         */
        @PostMapping("/{id}/media/finalize")
        @PreAuthorize("hasAnyRole('USER', 'DEALER', 'ADMIN')")
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
}
