package com.carselling.oldcar.listener;

import com.carselling.oldcar.event.media.MediaFinalizationRequestedEvent;
import com.carselling.oldcar.service.car.CarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AsyncMediaFinalizationListener {

    private final CarService carService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleMediaFinalizationEvent(MediaFinalizationRequestedEvent event) {
        log.info("Received Async MediaFinalizationRequestedEvent for car ID: {}", event.getCarId());
        try {
            carService.processAsyncMediaFinalization(
                    event.getCarId(),
                    event.getTempFileIds(),
                    event.getCurrentUserId());
        } catch (Exception e) {
            log.error("Async media finalization listener failed for car {}: {}", event.getCarId(), e.getMessage(), e);
        }
    }
}
