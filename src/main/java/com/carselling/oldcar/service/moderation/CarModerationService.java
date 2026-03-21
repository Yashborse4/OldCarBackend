package com.carselling.oldcar.service.moderation;

import com.carselling.oldcar.dto.moderation.ReportCarRequest;
import com.carselling.oldcar.exception.InvalidInputException;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarReport;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarReportRepository;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarModerationService {

    private final CarReportRepository carReportRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CarReport reportCar(ReportCarRequest request, Long reporterId) {
        log.info("User {} reporting car {}", reporterId, request.getCarId());

        // Check if user already reported this car
        carReportRepository.findByCarIdAndReporterId(request.getCarId(), reporterId)
                .ifPresent(report -> {
                    throw new InvalidInputException("You have already reported this car");
                });

        // Get car and reporter
        Car car = carRepository.findById(Long.parseLong(request.getCarId()))
                .orElseThrow(() -> new ResourceNotFoundException("Car", "id", request.getCarId()));

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", reporterId));

        // Prevent users from reporting their own cars
        if (car.getOwner().getId().equals(reporterId)) {
            throw new InvalidInputException("You cannot report your own car listing");
        }

        // Create car snapshot for moderation
        String carSnapshot = createCarSnapshot(car);

        // Create and save report
        CarReport report = CarReport.builder()
                .reportedCar(car)
                .reporter(reporter)
                .reason(request.getReason())
                .additionalComments(request.getAdditionalComments())
                .carSnapshot(carSnapshot)
                .build();

        return carReportRepository.save(report);
    }

    private String createCarSnapshot(Car car) {
        try {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("id", car.getId() != null ? car.getId().toString() : null);
            snapshot.put("make", car.getMake());
            snapshot.put("model", car.getModel());
            snapshot.put("year", car.getYear());
            snapshot.put("price", car.getPrice());
            if (car.getOwner() != null) {
                snapshot.put("sellerId", car.getOwner().getId());
                snapshot.put("sellerName", car.getOwner().getUsername());
            }
            snapshot.put("description", car.getDescription() != null ? car.getDescription() : "");
            snapshot.put("images", car.getImages() != null ? car.getImages().size() : 0);
            
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.error("Failed to create JSON snapshot of car: {}", car.getId(), e);
            return "{}";
        }
    }

    @Transactional(readOnly = true)
    public boolean hasUserReportedCar(String carId, Long userId) {
        return carReportRepository.findByCarIdAndReporterId(carId, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public Long getPendingReportsCount(String carId) {
        return carReportRepository.countPendingReportsByCarId(carId);
    }
}