package com.carselling.oldcar.service;

import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.CarInteractionEvent;
import com.carselling.oldcar.model.CarInteractionEvent.EventType;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarInteractionEventRepository;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarInteractionEventServiceTest {

        @Mock
        private CarInteractionEventRepository eventRepository;

        @Mock
        private CarRepository carRepository;

        @Mock
        private UserRepository userRepository;

        @InjectMocks
        private CarInteractionEventService eventService;

        private Car car;
        private User owner;
        private User otherUser;

        @BeforeEach
        void setUp() {
                owner = new User();
                owner.setId(1L);

                otherUser = new User();
                otherUser.setId(2L);

                car = new Car();
                car.setId(10L);
                car.setOwner(owner);

                lenient().when(carRepository.findById(10L)).thenReturn(Optional.of(car));
                lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
                lenient().when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        }

        @Test
        void trackEvent_skipsOwnerView() {
                eventService.trackEvent(10L, 1L, EventType.CAR_VIEW,
                                "session", "device", "ip", "ref", null);

                verify(eventRepository, never()).save(any(CarInteractionEvent.class));
        }

        @Test
        void trackEvent_skipsDuplicateViewSameDay() {
                when(eventRepository.existsViewByCarIdAndUserIdToday(eq(10L), eq(2L), any(LocalDateTime.class)))
                                .thenReturn(true);

                eventService.trackEvent(10L, 2L, EventType.CAR_VIEW,
                                "session", "device", "ip", "ref", null);

                verify(eventRepository, never()).save(any(CarInteractionEvent.class));
        }

        @Test
        void trackEvent_skipsDuplicateSaveForUserAndCar() {
                when(eventRepository.existsByCarIdAndUserIdAndEventType(10L, 2L, EventType.SAVE))
                                .thenReturn(true);

                eventService.trackEvent(10L, 2L, EventType.SAVE,
                                null, null, null, null, null);

                verify(eventRepository, never()).save(any(CarInteractionEvent.class));
        }

        @Test
        void trackEvent_persistsFirstSaveEvent() {
                when(eventRepository.existsByCarIdAndUserIdAndEventType(10L, 2L, EventType.SAVE))
                                .thenReturn(false);

                eventService.trackEvent(10L, 2L, EventType.SAVE,
                                "session-x", "device-x", "1.2.3.4", "ref-x", "{\"k\":\"v\"}");

                verify(eventRepository).save(any(CarInteractionEvent.class));
        }
}
