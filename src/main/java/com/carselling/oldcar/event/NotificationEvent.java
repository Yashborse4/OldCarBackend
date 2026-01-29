package com.carselling.oldcar.event;

import com.carselling.oldcar.model.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * Event published when a notification needs to be sent.
 * This decouples the triggering of a notification from the actual sending logic
 * (email, push, etc.).
 */
@Getter
public class NotificationEvent extends ApplicationEvent {

    private final User user;
    private final NotificationType type;
    private final String subject;
    private final String content;
    private final String actionUrl; // Button link or similar
    private final Map<String, Object> metadata;

    public NotificationEvent(Object source, User user, NotificationType type, String subject, String content,
            String actionUrl, Map<String, Object> metadata) {
        super(source);
        this.user = user;
        this.type = type;
        this.subject = subject;
        this.content = content;
        this.actionUrl = actionUrl;
        this.metadata = metadata;
    }

    public enum NotificationType {
        DEALER_UPGRADE,
        BATCH_JOB_COMPLETION,
        DEALER_VERIFICATION_APPROVED,
        DEALER_VERIFICATION_REVOKED,
        GENERAL_EMAIL
    }
}
