package com.carselling.oldcar.service.car;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.carselling.oldcar.dto.car.CarRequest;
import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.exception.BusinessException;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.*;
import com.carselling.oldcar.repository.*;
import com.carselling.oldcar.service.auth.AuthService;
import com.carselling.oldcar.service.file.ViewCountService;
import com.carselling.oldcar.service.AuditLogService;
import com.carselling.oldcar.service.file.FileValidationService;
import com.carselling.oldcar.service.media.MediaFinalizationService;
import com.carselling.oldcar.b2.B2FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

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
    private UploadedFileRepository uploadedFileRepository;
    @Mock
    private TemporaryFileRepository temporaryFileRepository;
    @Mock
    private MediaFinalizationService mediaFinalizationService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ViewCountService viewCountService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private CarServiceImpl carService;

    private User owner;
    private Car car;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");
        owner.setRole(Role.USER);
        owner.setLocation("New York");

        car = Car.builder()
                .id(100L)
                .make("Maruti")
                .model("Swift")
                .year(2020)
                .price(BigDecimal.valueOf(500000))
                .owner(owner)
                .status(CarStatus.PUBLISHED)
                .mediaStatus(MediaStatus.READY)
                .isActive(true)
                .isSold(false)
                .version(1L)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();

        lenient().when(carRepository.findWithDetailsById(100L)).thenReturn(Optional.of(car));
        lenient().when(carRepository.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
    }

    // --- Read Tests ---

    @Test
    void getVehicleById_Success_Owner() {
        when(authService.getCurrentUserOrNull()).thenReturn(owner);
        CarResponse response = carService.getVehicleById("100");
        assertNotNull(response);
        assertEquals("100", response.getId());
        verify(viewCountService).incrementAsync(eq(100L), eq(1L), eq(1L));
    }

    @Test
    void getVehicleById_Success_Public() {
        when(authService.getCurrentUserOrNull()).thenReturn(null);
        CarResponse response = carService.getVehicleById("100");
        assertNotNull(response);
        assertEquals("100", response.getId());
        verify(viewCountService).incrementAsync(eq(100L), isNull(), eq(1L));
    }

    @Test
    void getVehicleById_Success_Admin_EvenIfInactive() {
        User admin = new User();
        admin.setId(999L);
        admin.setRole(Role.ADMIN);
        car.setIsActive(false);
        when(authService.getCurrentUserOrNull()).thenReturn(admin);
        CarResponse response = carService.getVehicleById("100");
        assertNotNull(response);
        verify(viewCountService).incrementAsync(eq(100L), eq(999L), eq(1L));
    }

    @Test
    void getVehicleById_NotFound_Deleted() {
        car.setStatus(CarStatus.DELETED);
        assertThrows(ResourceNotFoundException.class, () -> carService.getVehicleById("100"));
    }

    @Test
    void getVehicleById_NotFound_PrivateAndNotOwner() {
        User stranger = new User();
        stranger.setId(2L);
        stranger.setRole(Role.USER);
        car.setIsActive(false);
        when(authService.getCurrentUserOrNull()).thenReturn(stranger);
        assertThrows(ResourceNotFoundException.class, () -> carService.getVehicleById("100"));
    }

    // --- Update Tests ---

    @Test
    void updateVehicle_rejectsNegativePrice() {
        CarRequest request = CarRequest.builder()
                .make("Maruti")
                .model("Swift")
                .year(2020)
                .price(BigDecimal.valueOf(-100))
                .build();
        assertThrows(BusinessException.class, () -> carService.updateVehicle("100", request, 1L));
    }

    @Test
    void updateVehicle_rejectsPriceChangeForSoldCar() {
        car.setStatus(CarStatus.SOLD);
        CarRequest request = CarRequest.builder()
                .make("Maruti")
                .model("Swift")
                .year(2020)
                .price(BigDecimal.valueOf(600000))
                .build();
        assertThrows(BusinessException.class, () -> carService.updateVehicle("100", request, 1L));
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
        assertThrows(BusinessException.class, () -> carService.updateVehicle("100", request, 1L));
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
        carService.updateVehicle("100", request, 99L);
        assertEquals(BigDecimal.valueOf(1000000), car.getPrice());
    }

    @Test
    void updateVehicle_triggersSecurityAuditForLargeChange() {
        CarRequest request = CarRequest.builder()
                .make("Maruti")
                .model("Swift")
                .year(2020)
                .price(BigDecimal.valueOf(800000)) // 60% increase
                .build();
        carService.updateVehicle("100", request, 1L);
        verify(auditLogService).logSecurityEvent(any(), any(), any(), any(), any());
    }

    // --- Media Tests ---

    @Test
    void uploadMedia_rejectsWhenCarSold() {
        car.setStatus(CarStatus.SOLD);
        assertThrows(BusinessException.class, () -> carService.uploadMedia("100", List.of("https://example.com/a.jpg"), null, 1L));
    }

    @Test
    void finalizeMedia_rejectsWhenCarArchived() {
        car.setStatus(CarStatus.ARCHIVED);
        assertThrows(BusinessException.class, () -> carService.finalizeMedia("100", List.of(1L, 2L), 1L));
    }

    @Test
    void uploadMedia_deletesReplacedImagesAndVideo() {
        car.setImages(new ArrayList<>(List.of("https://old.com/a.jpg", "https://old.com/b.jpg")));
        car.setImageUrl("https://old.com/a.jpg");
        car.setVideoUrl("https://old.com/old.mp4");

        List<String> newImages = List.of("https://old.com/b.jpg", "https://new.com/c.jpg");
        String newVideo = "https://new.com/new.mp4";

        carService.uploadMedia("100", newImages, newVideo, 1L);

        assertEquals(newImages, car.getImages());
        assertEquals(newVideo, car.getVideoUrl());
        assertEquals(MediaStatus.READY, car.getMediaStatus());

        verify(b2FileService).deleteFile("https://old.com/a.jpg");
        verify(b2FileService).deleteFile("https://old.com/old.mp4");
        verify(b2FileService, never()).deleteFile("https://old.com/b.jpg");
    }
}
