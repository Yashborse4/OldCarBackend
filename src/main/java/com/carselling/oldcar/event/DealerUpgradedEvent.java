package com.carselling.oldcar.event;

import com.carselling.oldcar.model.User;
import org.springframework.context.ApplicationEvent;

public class DealerUpgradedEvent extends ApplicationEvent {

    private final User user;

    public DealerUpgradedEvent(Object source, User user) {
        super(source);
        this.user = user;
    }

    public User getUser() {
        return user;
    }
}

