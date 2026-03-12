package com.carselling.oldcar.service.mail;

import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for sending emails via SMTP.
 * Uses spring.mail.username as the sender address with a fallback
 * to app.email.from or a hardcoded default to prevent AddressException
 * when the environment variable is empty.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.email.from:}")
    private String emailFrom;

    private String resolvedFromEmail;

    /**
     * Resolves the "from" email address at startup.
     * Priority: spring.mail.username -> app.email.from
     */
    @PostConstruct
    public void init() {
        if (mailUsername != null && !mailUsername.trim().isEmpty()) {
            resolvedFromEmail = mailUsername.trim();
        } else if (emailFrom != null && !emailFrom.trim().isEmpty()) {
            resolvedFromEmail = emailFrom.trim();
        } else {
            log.error("CRITICAL: No email sender address configured! " +
                    "(spring.mail.username and app.email.from are both empty). " +
                    "Email sending will likely fail.");
        }
        log.info("Email sender address resolved to: {}", resolvedFromEmail);
    }

    @Async
    public void sendTextEmail(String to, String subject, String body) {
        try {
            log.info("Sending email to: {}", to);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(resolvedFromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false); // false = plain text

            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}", to, e);
            // Don't rethrow to avoid blocking the user flow, but ensure it's logged
            // In a production system, you might want to queue failed emails
        }
    }

    @Async
    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            log.info("Sending HTML email to: {}", to);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(resolvedFromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML

            mailSender.send(message);
            log.info("HTML Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send HTML email to {}", to, e);
        }
    }

    @Async
    public void sendOtpEmail(String to, String otpCode, String purpose) {
        if (to == null || to.isBlank()) {
            log.warn("Skipping OTP email send because recipient address is blank");
            return;
        }
        if (otpCode == null || otpCode.isBlank()) {
            log.warn("Skipping OTP email send to {} because OTP code is blank", to);
            return;
        }

        String normalizedPurpose = purpose != null ? purpose : "";

        String subject = "Your Verification Code - Wheel Deal";
        String actionDescription = "verify your email address";
        if ("PASSWORD_RESET".equalsIgnoreCase(normalizedPurpose)) {
            subject = "Password Reset Request - Wheel Deal";
            actionDescription = "reset your password";
        } else if ("LOGIN".equalsIgnoreCase(normalizedPurpose)) {
            subject = "Your Login Code - Wheel Deal";
            actionDescription = "log in to your account";
        }

        String htmlTemplate = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { margin: 0; padding: 0; font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #F8FAFC; color: #1E293B; }
                        .wrapper { width: 100%; table-layout: fixed; background-color: #F8FAFC; padding-bottom: 40px; }
                        .container { max-width: 600px; margin: 40px auto; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05); }
                        .header { background: linear-gradient(135deg, #0F172A 0%, #1E40AF 100%); padding: 40px 20px; text-align: center; }
                        .logo-text { color: #FFFFFF; font-size: 28px; font-weight: 800; letter-spacing: -0.5px; margin: 0; text-transform: uppercase; }
                        .logo-accent { color: #EAB308; }
                        .content { padding: 40px 30px; text-align: center; }
                        .title { font-size: 22px; font-weight: 700; color: #0F172A; margin-bottom: 12px; }
                        .description { font-size: 16px; line-height: 1.6; color: #64748B; margin-bottom: 32px; }
                        .otp-container { background-color: #F1F5F9; border-radius: 12px; padding: 24px; margin: 0 auto 32px auto; display: inline-block; min-width: 260px; }
                        .otp-code { font-size: 42px; font-weight: 800; color: #1E40AF; letter-spacing: 8px; margin: 0; font-family: 'Courier New', monospace; }
                        .expiry-tag { display: inline-block; background-color: #FEF2F2; color: #EF4444; font-size: 13px; font-weight: 600; padding: 6px 12px; border-radius: 20px; margin-bottom: 24px; }
                        .divider { height: 1px; background-color: #E2E8F0; margin: 32px 0; }
                        .security-note { font-size: 13px; color: #94A3B8; margin-bottom: 0; }
                        .footer { background-color: #F8FAFC; padding: 32px 20px; text-align: center; }
                        .footer-text { font-size: 12px; color: #94A3B8; line-height: 1.5; margin: 0 0 8px 0; }
                        .social-links { margin: 16px 0; }
                        .social-icon { display: inline-block; width: 32px; height: 32px; margin: 0 8px; }
                    </style>
                </head>
                <body>
                    <div class="wrapper">
                        <div class="container">
                            <div class="header">
                                <h2 class="logo-text">WHEEL<span class="logo-accent">DEAL</span></h2>
                            </div>
                            <div class="content">
                                <h1 class="title">Verification Code</h1>
                                <p class="description">Hello,<br>Use the secure code below to {{ACTION_DESCRIPTION}}.</p>
                                
                                <div class="otp-container">
                                    <p class="otp-code">{{OTP_CODE}}</p>
                                </div>
                                
                                <div class="expiry-tag">
                                    Expires in 5 minutes
                                </div>
                                
                                <p class="description" style="font-size: 14px;">If you didn't request this, you can safely ignore this email.</p>
                                
                                <div class="divider"></div>
                                
                                <p class="security-note">For your security, never share this code with anyone.</p>
                            </div>
                            <div class="footer">
                                <p class="footer-text">&copy; {{CURRENT_YEAR}} Wheel Deal. All rights reserved.</p>
                                <p class="footer-text">This is an automated security message.</p>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """;

        try {
            String htmlContent = htmlTemplate
                    .replace("{{ACTION_DESCRIPTION}}", actionDescription)
                    .replace("{{OTP_CODE}}", otpCode)
                    .replace("{{CURRENT_YEAR}}", String.valueOf(java.time.Year.now().getValue()));

            sendHtmlEmail(to, subject, htmlContent);
        } catch (Exception e) {
            log.error("Unexpected error while preparing OTP email for {}: {}", to, e.getMessage(), e);
        }
    }
}
