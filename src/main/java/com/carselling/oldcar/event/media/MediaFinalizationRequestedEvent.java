package com.carselling.oldcar.event.media;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class MediaFinalizationRequestedEvent extends ApplicationEvent {
    private final Long carId;
    private final List<Long> tempFileIds;
    private final Long currentUserId;

    public MediaFinalizationRequestedEvent(Object source, Long carId, List<Long> tempFileIds, Long currentUserId) {
        super(source);
        this.carId = carId;
        this.tempFileIds = tempFileIds;
        this.currentUserId = currentUserId;
    }
}
