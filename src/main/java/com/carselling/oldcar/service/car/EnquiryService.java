package com.carselling.oldcar.service.car;

import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.Enquiry;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.EnquiryRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnquiryService {

    private final EnquiryRepository enquiryRepository;
    private final CarRepository carRepository;
    private final UserRepository userRepository;

    @Transactional
    public Enquiry createEnquiry(Long userId, Long carId, Enquiry.EnquiryType type, String message, String preferredTimeSlot, LocalDateTime scheduledTime) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Car car = carRepository.findById(carId)
                .orElseThrow(() -> new RuntimeException("Car not found"));

        Enquiry enquiry = Enquiry.builder()
                .user(user)
                .car(car)
                .type(type)
                .status(Enquiry.EnquiryStatus.NEW)
                .message(message)
                .preferredTimeSlot(preferredTimeSlot)
                .scheduledTime(scheduledTime)
                .build();

        log.info("Creating enquiry of type {} for car {} by user {}", type, carId, userId);
        return enquiryRepository.save(enquiry);
    }

    public Page<Enquiry> getDealerEnquiries(Long dealerId, Pageable pageable) {
        return enquiryRepository.findByCarOwnerId(dealerId, pageable);
    }

    public Page<Enquiry> getUserEnquiries(Long userId, Pageable pageable) {
        return enquiryRepository.findByUserId(userId, pageable);
    }

    @Transactional
    public Enquiry updateEnquiryStatus(Long enquiryId, Enquiry.EnquiryStatus status) {
        Enquiry enquiry = enquiryRepository.findById(enquiryId)
                .orElseThrow(() -> new RuntimeException("Enquiry not found"));
        enquiry.setStatus(status);
        return enquiryRepository.save(enquiry);
    }
}
