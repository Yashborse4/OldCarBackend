package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.email.EmailRequest;
import com.carselling.oldcar.dto.email.EmailResponse;
import com.carselling.oldcar.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling email operations including templates and notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@carselling.com}")
    private String fromEmail;

    @Value("${app.name:Car Selling Platform}")
    private String appName;

    @Value("${app.url:https://carselling.com}")
    private String appUrl;

    // ========================= VERIFICATION EMAILS =========================

    /**
     * Send email verification link to user
     */
    @Async
    public CompletableFuture<EmailResponse> sendVerificationEmail(User user, String verificationToken) {
        try {
            String verificationUrl = appUrl + "/auth/verify-email?token=" + verificationToken;
            
            String subject = "Verify Your Email Address - " + appName;
            String htmlContent = buildVerificationEmailTemplate(user.getFullName(), verificationUrl);
            
            sendHtmlEmail(user.getEmail(), subject, htmlContent);
            
            log.info("Verification email sent to: {}", user.getEmail());
            
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(true)
                    .recipient(user.getEmail())
                    .subject(subject)
                    .sentAt(LocalDateTime.now())
                    .build());
                    
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage(), e);
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(false)
                    .recipient(user.getEmail())
                    .errorMessage(e.getMessage())
                    .sentAt(LocalDateTime.now())
                    .build());
        }
    }

    /**
     * Send password reset email
     */
    @Async
    public CompletableFuture<EmailResponse> sendPasswordResetEmail(User user, String resetToken) {
        try {
            String resetUrl = appUrl + "/auth/reset-password?token=" + resetToken;
            
            String subject = "Password Reset Request - " + appName;
            String htmlContent = buildPasswordResetEmailTemplate(user.getFullName(), resetUrl);
            
            sendHtmlEmail(user.getEmail(), subject, htmlContent);
            
            log.info("Password reset email sent to: {}", user.getEmail());
            
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(true)
                    .recipient(user.getEmail())
                    .subject(subject)
                    .sentAt(LocalDateTime.now())
                    .build());
                    
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage(), e);
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(false)
                    .recipient(user.getEmail())
                    .errorMessage(e.getMessage())
                    .sentAt(LocalDateTime.now())
                    .build());
        }
    }

    // ========================= NOTIFICATION EMAILS =========================

    /**
     * Send car inquiry notification to seller
     */
    @Async
    public CompletableFuture<EmailResponse> sendCarInquiryNotification(
            User seller, User buyer, String carTitle, String inquiryMessage) {
        try {
            String subject = "New Inquiry for Your Car: " + carTitle;
            String htmlContent = buildCarInquiryEmailTemplate(seller.getFullName(), buyer.getFullName(), 
                                                               carTitle, inquiryMessage);
            
            sendHtmlEmail(seller.getEmail(), subject, htmlContent);
            
            log.info("Car inquiry notification sent to seller: {}", seller.getEmail());
            
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(true)
                    .recipient(seller.getEmail())
                    .subject(subject)
                    .sentAt(LocalDateTime.now())
                    .build());
                    
        } catch (Exception e) {
            log.error("Failed to send car inquiry notification: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(false)
                    .recipient(seller.getEmail())
                    .errorMessage(e.getMessage())
                    .sentAt(LocalDateTime.now())
                    .build());
        }
    }

    /**
     * Send price drop alert to interested users
     */
    @Async
    public CompletableFuture<EmailResponse> sendPriceDropAlert(
            User user, String carTitle, String oldPrice, String newPrice, String carUrl) {
        try {
            String subject = "Price Drop Alert: " + carTitle;
            String htmlContent = buildPriceDropAlertTemplate(user.getFullName(), carTitle, 
                                                              oldPrice, newPrice, carUrl);
            
            sendHtmlEmail(user.getEmail(), subject, htmlContent);
            
            log.info("Price drop alert sent to: {}", user.getEmail());
            
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(true)
                    .recipient(user.getEmail())
                    .subject(subject)
                    .sentAt(LocalDateTime.now())
                    .build());
                    
        } catch (Exception e) {
            log.error("Failed to send price drop alert: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(false)
                    .recipient(user.getEmail())
                    .errorMessage(e.getMessage())
                    .sentAt(LocalDateTime.now())
                    .build());
        }
    }

    /**
     * Send new message notification
     */
    @Async
    public CompletableFuture<EmailResponse> sendNewMessageNotification(
            User recipient, String senderName, String messagePreview, String chatUrl) {
        try {
            String subject = "New Message from " + senderName;
            String htmlContent = buildNewMessageNotificationTemplate(recipient.getFullName(), 
                                                                     senderName, messagePreview, chatUrl);
            
            sendHtmlEmail(recipient.getEmail(), subject, htmlContent);
            
            log.info("New message notification sent to: {}", recipient.getEmail());
            
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(true)
                    .recipient(recipient.getEmail())
                    .subject(subject)
                    .sentAt(LocalDateTime.now())
                    .build());
                    
        } catch (Exception e) {
            log.error("Failed to send message notification: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(false)
                    .recipient(recipient.getEmail())
                    .errorMessage(e.getMessage())
                    .sentAt(LocalDateTime.now())
                    .build());
        }
    }

    // ========================= MARKETING EMAILS =========================

    /**
     * Send welcome email to new users
     */
    @Async
    public CompletableFuture<EmailResponse> sendWelcomeEmail(User user) {
        try {
            String subject = "Welcome to " + appName + "!";
            String htmlContent = buildWelcomeEmailTemplate(user.getFullName());
            
            sendHtmlEmail(user.getEmail(), subject, htmlContent);
            
            log.info("Welcome email sent to: {}", user.getEmail());
            
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(true)
                    .recipient(user.getEmail())
                    .subject(subject)
                    .sentAt(LocalDateTime.now())
                    .build());
                    
        } catch (Exception e) {
            log.error("Failed to send welcome email: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(false)
                    .recipient(user.getEmail())
                    .errorMessage(e.getMessage())
                    .sentAt(LocalDateTime.now())
                    .build());
        }
    }

    /**
     * Send newsletter/promotional email
     */
    @Async
    public CompletableFuture<EmailResponse> sendNewsletterEmail(
            User user, String newsletterTitle, String newsletterContent) {
        try {
            String subject = newsletterTitle + " - " + appName;
            String htmlContent = buildNewsletterEmailTemplate(user.getFullName(), 
                                                              newsletterTitle, newsletterContent);
            
            sendHtmlEmail(user.getEmail(), subject, htmlContent);
            
            log.info("Newsletter email sent to: {}", user.getEmail());
            
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(true)
                    .recipient(user.getEmail())
                    .subject(subject)
                    .sentAt(LocalDateTime.now())
                    .build());
                    
        } catch (Exception e) {
            log.error("Failed to send newsletter email: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(false)
                    .recipient(user.getEmail())
                    .errorMessage(e.getMessage())
                    .sentAt(LocalDateTime.now())
                    .build());
        }
    }

    // ========================= GENERIC EMAIL METHODS =========================

    /**
     * Send custom email with template variables
     */
    @Async
    public CompletableFuture<EmailResponse> sendCustomEmail(EmailRequest emailRequest) {
        try {
            sendHtmlEmail(emailRequest.getRecipient(), emailRequest.getSubject(), 
                         emailRequest.getContent());
            
            log.info("Custom email sent to: {}", emailRequest.getRecipient());
            
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(true)
                    .recipient(emailRequest.getRecipient())
                    .subject(emailRequest.getSubject())
                    .sentAt(LocalDateTime.now())
                    .build());
                    
        } catch (Exception e) {
            log.error("Failed to send custom email: {}", e.getMessage(), e);
            return CompletableFuture.completedFuture(EmailResponse.builder()
                    .success(false)
                    .recipient(emailRequest.getRecipient())
                    .errorMessage(e.getMessage())
                    .sentAt(LocalDateTime.now())
                    .build());
        }
    }

    // ========================= PRIVATE HELPER METHODS =========================

    /**
     * Send HTML email
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        
        mailSender.send(message);
    }

    /**
     * Send simple text email
     */
    private void sendTextEmail(String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        
        mailSender.send(message);
    }

    // ========================= EMAIL TEMPLATES =========================

    private String buildVerificationEmailTemplate(String userName, String verificationUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c5aa0;">Welcome to %s!</h2>
                    <p>Hi %s,</p>
                    <p>Thank you for joining our car selling platform. To get started, please verify your email address by clicking the button below:</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background-color: #2c5aa0; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-weight: bold;">Verify Email Address</a>
                    </div>
                    <p>If the button doesn't work, copy and paste this link into your browser:</p>
                    <p style="word-break: break-all; color: #666;">%s</p>
                    <p>This verification link will expire in 24 hours.</p>
                    <hr style="margin: 30px 0; border: none; border-top: 1px solid #eee;">
                    <p style="font-size: 12px; color: #666;">
                        If you didn't create an account with us, please ignore this email.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(appName, userName, verificationUrl, verificationUrl);
    }

    private String buildPasswordResetEmailTemplate(String userName, String resetUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c5aa0;">Password Reset Request</h2>
                    <p>Hi %s,</p>
                    <p>We received a request to reset your password. Click the button below to create a new password:</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background-color: #dc3545; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-weight: bold;">Reset Password</a>
                    </div>
                    <p>If the button doesn't work, copy and paste this link into your browser:</p>
                    <p style="word-break: break-all; color: #666;">%s</p>
                    <p>This reset link will expire in 1 hour for security reasons.</p>
                    <hr style="margin: 30px 0; border: none; border-top: 1px solid #eee;">
                    <p style="font-size: 12px; color: #666;">
                        If you didn't request a password reset, please ignore this email. Your password remains unchanged.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(userName, resetUrl, resetUrl);
    }

    private String buildCarInquiryEmailTemplate(String sellerName, String buyerName, String carTitle, String message) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c5aa0;">New Car Inquiry</h2>
                    <p>Hi %s,</p>
                    <p>You have a new inquiry for your vehicle: <strong>%s</strong></p>
                    <div style="background-color: #f8f9fa; padding: 15px; border-left: 4px solid #2c5aa0; margin: 20px 0;">
                        <h3 style="margin: 0 0 10px 0;">From: %s</h3>
                        <p style="margin: 0;">%s</p>
                    </div>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s/dashboard" style="background-color: #28a745; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-weight: bold;">View in Dashboard</a>
                    </div>
                    <p>Respond quickly to increase your chances of a sale!</p>
                </div>
            </body>
            </html>
            """.formatted(sellerName, carTitle, buyerName, message, appUrl);
    }

    private String buildPriceDropAlertTemplate(String userName, String carTitle, String oldPrice, String newPrice, String carUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #dc3545;">🔥 Price Drop Alert!</h2>
                    <p>Hi %s,</p>
                    <p>Great news! The price for a car you're interested in has been reduced:</p>
                    <div style="background-color: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0;">
                        <h3 style="margin: 0 0 15px 0; color: #2c5aa0;">%s</h3>
                        <p style="margin: 0;">
                            <span style="text-decoration: line-through; color: #666;">%s</span>
                            <span style="font-size: 24px; font-weight: bold; color: #dc3545; margin-left: 10px;">%s</span>
                        </p>
                    </div>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background-color: #ffc107; color: black; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-weight: bold;">View Car Details</a>
                    </div>
                    <p>Don't miss out on this opportunity!</p>
                </div>
            </body>
            </html>
            """.formatted(userName, carTitle, oldPrice, newPrice, carUrl);
    }

    private String buildNewMessageNotificationTemplate(String userName, String senderName, String messagePreview, String chatUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c5aa0;">New Message</h2>
                    <p>Hi %s,</p>
                    <p>You have a new message from <strong>%s</strong>:</p>
                    <div style="background-color: #f8f9fa; padding: 15px; border-left: 4px solid #2c5aa0; margin: 20px 0;">
                        <p style="margin: 0; font-style: italic;">"%s"</p>
                    </div>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background-color: #2c5aa0; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-weight: bold;">Reply Now</a>
                    </div>
                    <p>Stay connected and respond quickly!</p>
                </div>
            </body>
            </html>
            """.formatted(userName, senderName, messagePreview, chatUrl);
    }

    private String buildWelcomeEmailTemplate(String userName) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c5aa0;">Welcome to %s! 🚗</h2>
                    <p>Hi %s,</p>
                    <p>Welcome to the best place to buy and sell cars online! We're excited to have you join our community.</p>
                    <div style="background-color: #f8f9fa; padding: 20px; border-radius: 8px; margin: 20px 0;">
                        <h3 style="margin: 0 0 15px 0;">Getting Started:</h3>
                        <ul style="margin: 0; padding-left: 20px;">
                            <li>Complete your profile for better visibility</li>
                            <li>Browse thousands of cars from verified sellers</li>
                            <li>Use our chat feature to connect with buyers/sellers</li>
                            <li>Get real-time notifications on price drops</li>
                        </ul>
                    </div>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s/dashboard" style="background-color: #28a745; color: white; padding: 12px 30px; text-decoration: none; border-radius: 5px; font-weight: bold;">Explore Now</a>
                    </div>
                    <p>Happy car hunting!</p>
                </div>
            </body>
            </html>
            """.formatted(appName, userName, appUrl);
    }

    private String buildNewsletterEmailTemplate(String userName, String title, String content) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c5aa0;">%s</h2>
                    <p>Hi %s,</p>
                    <div style="margin: 20px 0;">
                        %s
                    </div>
                    <hr style="margin: 30px 0; border: none; border-top: 1px solid #eee;">
                    <p style="font-size: 12px; color: #666;">
                        You're receiving this email because you subscribed to updates from %s. 
                        <a href="%s/unsubscribe" style="color: #666;">Unsubscribe</a>
                    </p>
                </div>
            </body>
            </html>
            """.formatted(title, userName, content, appName, appUrl);
    }
}
