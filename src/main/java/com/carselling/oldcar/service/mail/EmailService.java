package com.carselling.oldcar.service.mail;

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
 * Service for sending emails via SMTP
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendTextEmail(String to, String subject, String body) {
        try {
            log.info("Sending email to: {}", to);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
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

            helper.setFrom(fromEmail);
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

        String subject = "Your Verification Code - Car World";
        String actionDescription = "verify your email address";
        if ("PASSWORD_RESET".equalsIgnoreCase(normalizedPurpose)) {
            subject = "Password Reset Request - Car World";
            actionDescription = "reset your password";
        } else if ("LOGIN".equalsIgnoreCase(normalizedPurpose)) {
            subject = "Your Login Code - Car World";
            actionDescription = "log in to your account";
        }

        String htmlTemplate = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Verification Code</title>
                    <style>
                        body { margin: 0; padding: 0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f4f4; }
                        .container { max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.1); margin-top: 40px; margin-bottom: 40px; }
                        .header { background: linear-gradient(135deg, #1a73e8 0%, #0d47a1 100%); padding: 30px; text-align: center; color: white; }
                        .header h1 { margin: 0; font-size: 24px; font-weight: 600; letter-spacing: 1px; }
                        .content { padding: 40px; text-align: center; color: #333333; }
                        .otp-box { background-color: #f8f9fa; border: 2px dashed #0d47a1; border-radius: 8px; padding: 20px; margin: 30px auto; width: fit-content; min-width: 200px; }
                        .otp-code { font-size: 36px; font-weight: 700; color: #0d47a1; letter-spacing: 5px; margin: 0; font-family: 'Courier New', monospace; }
                        .message { font-size: 16px; line-height: 1.6; color: #555555; margin-bottom: 20px; }
                        .footer { background-color: #f8f9fa; padding: 20px; text-align: center; font-size: 12px; color: #888888; border-top: 1px solid #eeeeee; }
                        .expiry { color: #d32f2f; font-weight: 500; font-size: 14px; margin-top: 15px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>Car World</h1>
                        </div>
                        <div class="content">
                            <p class="message">Hello,</p>
                            <p class="message">Use the code below to {{ACTION_DESCRIPTION}}.</p>

                            <div class="otp-box">
                                <p class="otp-code">{{OTP_CODE}}</p>
                            </div>

                            <p class="expiry">This code will expire in 5 minutes.</p>
                            <p class="message" style="font-size: 14px; margin-top: 30px;">If you didn't request this code, you can safely ignore this email.</p>
                        </div>
                        <div class="footer">
                            <p>&copy; {{CURRENT_YEAR}} Car World. All rights reserved.</p>
                            <p>This is an automated message, please do not reply.</p>
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
