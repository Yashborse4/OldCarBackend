package com.carselling.oldcar.service.car;

import com.carselling.oldcar.dto.car.CarReportDto;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarReport;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.CarReportRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CarReportService {

    private final CarReportRepository carReportRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;

    @Transactional
    public void submitReport(Long carId, Long reporterId, CarReport.CarReportReason reason, String comments) {
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));

        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Prevent duplicate pending reports from same user for same car
        if (carReportRepository.findByCarIdAndReporterId(String.valueOf(carId), reporterId).isPresent()) {
            log.info("User {} already reported car {}. Skipping.", reporterId, carId);
            return;
        }

        CarReport report = CarReport.builder()
                .reportedCar(car)
                .reporter(reporter)
                .reason(reason)
                .additionalComments(comments)
                .status(ReportStatus.PENDING)
                .carSnapshot(generateSnapshot(car))
                .build();

        carReportRepository.save(report);
        log.info("Car {} reported by user {} for reason: {}", carId, reporterId, reason);
    }

    public List<CarReport> getAllPendingReports() {
        // Implementation for moderator view
        return carReportRepository.findAll().stream()
                .filter(r -> r.getStatus() == ReportStatus.PENDING)
                .collect(Collectors.toList());
    }

    private String generateSnapshot(Car car) {
        // Minimal snapshot of car details at time of report
        return String.format("ID: %d | %s %s (%d) | Price: %s | Seller ID: %d",
                car.getId(), car.getMake(), car.getModel(), car.getYear(),
                car.getPrice(), car.getOwner() != null ? car.getOwner().getId() : 0);
    }
}
