package com.carselling.oldcar.service;

import com.carselling.oldcar.b2.B2FileService;
import com.carselling.oldcar.dto.car.CarRequest;
import com.carselling.oldcar.exception.BusinessException;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarStatus;
import com.carselling.oldcar.model.MediaStatus;
import com.carselling.oldcar.model.Role;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarMasterRepository;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.service.auth.AuthService;
import com.carselling.oldcar.service.car.CarServiceImpl;

import com.carselling.oldcar.service.file.FileValidationService;
import com.carselling.oldcar.service.media.MediaFinalizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarServiceImplTest {

        @Mock
        private CarRepository carRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private AuthService authService;
        @Mock
        private CarMasterRepository carMasterRepository;
        @Mock
        private B2FileService b2FileService;
        @Mock
        private FileValidationService fileValidationService;
        @Mock
        private MediaFinalizationService mediaFinalizationService;
        @Mock
        private AuditLogService auditLogService;

        @InjectMocks
        private CarServiceImpl carService;

        private User owner;
        private Car existingCar;

        @BeforeEach
        void setUp() {
                owner = new User();
                owner.setId(1L);
                owner.setUsername("dealer1");
                owner.setRole(Role.DEALER);

                existingCar = Car.builder()
                                .id(10L)
                                .make("Maruti")
                                .model("Swift")
                                .year(2020)
                                .price(BigDecimal.valueOf(500000))
                                .owner(owner)
                                .status(CarStatus.PUBLISHED)
                                .mediaStatus(MediaStatus.READY)
                                .createdAt(LocalDateTime.now().minusDays(1))
                                .updatedAt(LocalDateTime.now().minusHours(1))
                                .build();

                lenient().when(carRepository.findById(10L)).thenReturn(Optional.of(existingCar));
                lenient().when(carRepository.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));
                lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        }

        @Test
        void updateVehicle_rejectsNegativePrice() {
                CarRequest request = CarRequest.builder()
                                .make("Maruti")
                                .model("Swift")
                                .year(2020)
                                .price(BigDecimal.valueOf(-100))
                                .build();

                assertThrows(BusinessException.class,
                                () -> carService.updateVehicle("10", request, 1L));
        }

        @Test
        void updateVehicle_rejectsPriceChangeForSoldCar() {
                existingCar.setStatus(CarStatus.SOLD);

                CarRequest request = CarRequest.builder()
                                .make("Maruti")
                                .model("Swift")
                                .year(2020)
                                .price(BigDecimal.valueOf(600000))
                                .build();

                assertThrows(BusinessException.class,
                                () -> carService.updateVehicle("10", request, 1L));
        }

        @Test
        void updateVehicle_largePriceChangeNonAdminRejected() {
                CarRequest request = CarRequest.builder()
                                .make("Maruti")
                                .model("Swift")
                                .year(2020)
                                .price(BigDecimal.valueOf(1000000)) // 100% increase
                                .build();

                User nonAdmin = new User();
                nonAdmin.setId(1L);
                nonAdmin.setRole(Role.DEALER);
                when(userRepository.findById(1L)).thenReturn(Optional.of(nonAdmin));

                assertThrows(BusinessException.class,
                                () -> carService.updateVehicle("10", request, 1L));
        }

        @Test
        void updateVehicle_largePriceChangeAdminAllowed() {
                CarRequest request = CarRequest.builder()
                                .make("Maruti")
                                .model("Swift")
                                .year(2020)
                                .price(BigDecimal.valueOf(1000000))
                                .build();

                User admin = new User();
                admin.setId(99L);
                admin.setRole(Role.ADMIN);
                when(userRepository.findById(99L)).thenReturn(Optional.of(admin));

                carService.updateVehicle("10", request, 99L);

                assertEquals(BigDecimal.valueOf(1000000), existingCar.getPrice());
        }

        @Test
        void updateVehicle_triggersSecurityAuditForLargeChange() {
                CarRequest request = CarRequest.builder()
                                .make("Maruti")
                                .model("Swift")
                                .year(2020)
                                .price(BigDecimal.valueOf(800000)) // 60% increase
                                .build();

                carService.updateVehicle("10", request, 1L);

                verify(auditLogService).logSecurityEvent(
                                any(),
                                any(),
                                any(),
                                any(),
                                any());
        }

        @Test
        void uploadMedia_rejectsWhenCarSold() {
                existingCar.setStatus(CarStatus.SOLD);

                assertThrows(BusinessException.class,
                                () -> carService.uploadMedia("10", List.of("https://example.com/a.jpg"), null, 1L));
        }

        @Test
        void finalizeMedia_rejectsWhenCarArchived() {
                existingCar.setStatus(CarStatus.ARCHIVED);

                assertThrows(BusinessException.class,
                                () -> carService.finalizeMedia("10", List.of(1L, 2L), 1L));
        }

        @Test
        void uploadMedia_deletesReplacedImagesAndVideo() {
                existingCar.setImages(List.of("https://old.com/a.jpg", "https://old.com/b.jpg"));
                existingCar.setImageUrl("https://old.com/a.jpg");
                existingCar.setVideoUrl("https://old.com/old.mp4");

                List<String> newImages = List.of("https://old.com/b.jpg", "https://new.com/c.jpg");
                String newVideo = "https://new.com/new.mp4";

                carService.uploadMedia("10", newImages, newVideo, 1L);

                assertEquals(newImages, existingCar.getImages());
                assertEquals(newVideo, existingCar.getVideoUrl());
                assertEquals(MediaStatus.READY, existingCar.getMediaStatus());

                verify(b2FileService).deleteFile("https://old.com/a.jpg");
                verify(b2FileService).deleteFile("https://old.com/old.mp4");
                verify(b2FileService, never()).deleteFile("https://old.com/b.jpg");
        }
}
