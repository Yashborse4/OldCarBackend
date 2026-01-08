package com.carselling.oldcar.event;

import com.carselling.oldcar.model.User;
import org.springframework.context.ApplicationEvent;

public class DealerVerifiedEvent extends ApplicationEvent {

    private final User user;
    private final boolean verified;

    public DealerVerifiedEvent(Object source, User user, boolean verified) {
        super(source);
        this.user = user;
        this.verified = verified;
    }

    public User getUser() {
        return user;
    }

    public boolean isVerified() {
        return verified;
    }
}

