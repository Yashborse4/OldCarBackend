package com.carselling.oldcar.service.car;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.carselling.oldcar.dto.car.CarResponse;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.*;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.service.auth.AuthService;
import com.carselling.oldcar.service.file.ViewCountService;
import com.carselling.oldcar.service.AuditLogService;
import com.carselling.oldcar.service.file.FileValidationService;
import com.carselling.oldcar.service.media.MediaFinalizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private ViewCountService viewCountService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private com.carselling.oldcar.repository.CarMasterRepository carMasterRepository;
    @Mock
    private com.carselling.oldcar.b2.B2FileService b2FileService;
    @Mock
    private FileValidationService fileValidationService;
    @Mock
    private com.carselling.oldcar.repository.UploadedFileRepository uploadedFileRepository;
    @Mock
    private com.carselling.oldcar.repository.TemporaryFileRepository temporaryFileRepository;
    @Mock
    private MediaFinalizationService mediaFinalizationService;
    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

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

        car = new Car();
        car.setId(100L);
        car.setOwner(owner);
        car.setStatus(CarStatus.PUBLISHED);
        car.setIsActive(true);
        car.setIsSold(false);
        car.setMediaStatus(MediaStatus.READY);
        car.setVersion(1L);
    }

    @Test
    void getVehicleById_Success_Owner() {
        // Arrange
        when(authService.getCurrentUserOrNull()).thenReturn(owner);
        when(carRepository.findById(100L)).thenReturn(Optional.of(car));

        // Act
        CarResponse response = carService.getVehicleById("100");

        // Assert
        assertNotNull(response);
        assertEquals("100", response.getId());
        verify(viewCountService).incrementAsync(eq(100L), eq(1L), eq(1L));
    }

    @Test
    void getVehicleById_Success_Public() {
        // Arrange
        when(authService.getCurrentUserOrNull()).thenReturn(null);
        when(carRepository.findById(100L)).thenReturn(Optional.of(car));

        // Act
        CarResponse response = carService.getVehicleById("100");

        // Assert
        assertNotNull(response);
        assertEquals("100", response.getId());
        verify(viewCountService).incrementAsync(eq(100L), isNull(), eq(1L));
    }

    @Test
    void getVehicleById_Success_Admin_EvenIfInactive() {
        // Arrange
        User admin = new User();
        admin.setId(999L);
        admin.setRole(Role.ADMIN);

        car.setIsActive(false); // Not public

        when(authService.getCurrentUserOrNull()).thenReturn(admin);
        when(carRepository.findById(100L)).thenReturn(Optional.of(car));

        // Act
        CarResponse response = carService.getVehicleById("100");

        // Assert
        assertNotNull(response);
        verify(viewCountService).incrementAsync(eq(100L), eq(999L), eq(1L));
    }

    @Test
    void getVehicleById_NotFound_Deleted() {
        // Arrange
        car.setStatus(CarStatus.DELETED);
        when(carRepository.findById(100L)).thenReturn(Optional.of(car));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> carService.getVehicleById("100"));
    }

    @Test
    void getVehicleById_NotFound_PrivateAndNotOwner() {
        // Arrange
        User stranger = new User();
        stranger.setId(2L);
        stranger.setRole(Role.USER);

        car.setIsActive(false); // Private car

        when(authService.getCurrentUserOrNull()).thenReturn(stranger);
        when(carRepository.findById(100L)).thenReturn(Optional.of(car));

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> carService.getVehicleById("100"));
    }
}
