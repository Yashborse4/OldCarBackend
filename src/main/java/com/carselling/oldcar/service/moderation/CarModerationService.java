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
import com.carselling.oldcar.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarModerationService {

    private final CarReportRepository carReportRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;

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
        // Create JSON snapshot of car details for moderation
        return String.format(
                "{\"id\":\"%s\",\"make\":\"%s\",\"model\":\"%s\",\"year\":%d,\"price\":%f,\"sellerId\":%d,\"sellerName\":\"%s\",\"description\":\"%s\",\"images\":%d}",
                car.getId(),
                car.getMake(),
                car.getModel(),
                car.getYear(),
                car.getPrice(),
                car.getOwner().getId(),
                car.getOwner().getUsername(),
                car.getDescription() != null ? car.getDescription().replace("\"", "'" ) : "",
                car.getImages() != null ? car.getImages().size() : 0
        );
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