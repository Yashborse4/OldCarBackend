package com.carselling.oldcar.event.listener;

import com.carselling.oldcar.event.NotificationEvent;
import com.carselling.oldcar.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listener for NotificationEvents.
 * Processes notifications asynchronously to avoid blocking the main thread.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final EmailService emailService;

    @Async
    @EventListener
    public void handleNotificationEvent(NotificationEvent event) {
        log.info("Processing async notification event: Type={}, User={}", event.getType(), event.getUser().getEmail());

        try {
            switch (event.getType()) {
                case DEALER_UPGRADE:
                case BATCH_JOB_COMPLETION:
                case DEALER_VERIFICATION_APPROVED:
                case DEALER_VERIFICATION_REVOKED:
                case GENERAL_EMAIL:
                    // For now, all specific types flow through the generic email sender helper
                    // The content is already formatted in the event payload by the service
                    // In a more complex system, we might re-format here based on type
                    sendFormattedEmail(event);
                    break;
                default:
                    log.warn("Unknown notification type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Failed to process notification event for user {}: {}", event.getUser().getEmail(),
                    e.getMessage(), e);
        }
    }

    private void sendFormattedEmail(NotificationEvent event) {
        String htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { margin: 0; padding: 0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f4f4; }
                        .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); margin-top: 40px; margin-bottom: 40px; }
                        .header { background: linear-gradient(135deg, #1a73e8 0%, #0d47a1 100%); padding: 30px; text-align: center; color: white; }
                        .header h1 { margin: 0; font-size: 24px; font-weight: 600; letter-spacing: 1px; }
                        .content { padding: 40px; text-align: left; color: #333333; font-size: 16px; line-height: 1.6; }
                        .footer { background-color: #f8f9fa; padding: 20px; text-align: center; font-size: 12px; color: #888888; border-top: 1px solid #eeeeee; }
                        .button { display: inline-block; padding: 12px 24px; background-color: #0d47a1; color: white; text-decoration: none; border-radius: 6px; font-weight: 600; margin-top: 20px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Car World</h1>
                        </div>
                        <div class="content">
                            %s
                            <br>
                            <div style="text-align: center;">
                                <a href="#" class="button" style="color: white !important;">%s</a>
                            </div>
                        </div>
                        <div class="footer">
                            <p>&copy; %d Car World. All rights reserved.</p>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(event.getContent(), event.getActionUrl(), java.time.Year.now().getValue());

        emailService.sendHtmlEmail(event.getUser().getEmail(), event.getSubject(), htmlContent);
    }
}
