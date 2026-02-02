package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.CarStatistics;
import com.carselling.oldcar.dto.car.*;
import com.carselling.oldcar.model.MediaStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/**
 * Interface for Car Service Operations
 * Adheres to Dependency Inversion Principle (DIP)
 */
public interface CarService {

    Page<CarResponse> getAllVehicles(Pageable pageable);

    CarResponse getVehicleById(String id);

    Page<PublicCarDTO> getPublicVehicles(Pageable pageable);

    PublicCarDTO getPublicVehicleById(String id);

    PrivateCarDTO getPrivateVehicleById(String id);

    PublicCarDTO convertToPublicDTO(com.carselling.oldcar.model.Car car);

    PrivateCarDTO convertToPrivateDTO(com.carselling.oldcar.model.Car car);

    CarResponse findByIdempotencyKey(String idempotencyKey, Long ownerId);

    CarResponse createVehicle(CarRequest request, Long currentUserId, String idempotencyKey);

    CarResponse updateVehicle(String id, CarRequest request, Long currentUserId);

    CarResponse updateVehicleWithMedia(String id, CarRequest request,
            List<MultipartFile> images, Long currentUserId);

    void deleteVehicle(String id, Long currentUserId, boolean hard);

    void softDeleteUserCars(Long userId);

    CarResponse updateVehicleStatus(String id, String status, Long currentUserId);

    CarResponse toggleVisibility(String id, boolean visible, Long currentUserId);

    CarResponse updateMediaStatus(String id, MediaStatus status, Long currentUserId);

    CarResponse uploadMedia(String id, java.util.List<String> imageUrls, String videoUrl, Long currentUserId);

    Page<CarResponse> searchVehicles(CarSearchCriteria criteria, Pageable pageable);

    CarAnalyticsResponse getVehicleAnalytics(String id, Long currentUserId);

    CarResponse toggleFeatureVehicle(String id, boolean featured, Long currentUserId);

    void trackVehicleView(String id);

    void trackVehicleInquiry(String id);

    void trackVehicleShare(String id, String platform);

    Page<CarResponse> getVehiclesByDealer(String dealerId, String status, Pageable pageable);

    CarStatistics getCarStatistics();

    void incrementCarStat(String id, String statType);

    CarResponse finalizeMedia(String id, java.util.List<Long> tempFileIds, Long currentUserId);

    boolean isVehicleOwner(String id, Long userId);

    Long getCarOwnerId(String carId);

    java.util.List<CarResponse> getVehiclesByIds(java.util.List<String> ids);
}
