package com.carselling.oldcar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for audit logging and security monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    // In-memory audit tracking for immediate alerting
    private final Map<String, AtomicLong> failedLoginAttempts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastFailedAttempt = new ConcurrentHashMap<>();

    /**
     * Log authentication events
     */
    @Async
    public void logAuthenticationEvent(String username, String eventType, String ipAddress, 
                                     String userAgent, boolean success, String details) {
        try {
            if (!success) {
                // Track failed attempts
                String key = ipAddress + ":" + username;
                failedLoginAttempts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
                lastFailedAttempt.put(key, LocalDateTime.now());
                
                log.warn("Failed authentication attempt: {} from IP: {} - {}", username, ipAddress, details);
                
                // Alert on multiple failed attempts
                if (failedLoginAttempts.get(key).get() >= 5) {
                    logSecurityEvent("BRUTE_FORCE_ATTEMPT", username, ipAddress, 
                        "Multiple failed login attempts detected", "HIGH");
                }
            } else {
                // Reset failed attempts on successful login
                String key = ipAddress + ":" + username;
                failedLoginAttempts.remove(key);
                lastFailedAttempt.remove(key);
                
                log.info("Successful authentication: {} from IP: {}", username, ipAddress);
            }
        } catch (Exception e) {
            log.error("Error logging authentication event: {}", e.getMessage());
        }
    }

    /**
     * Log API access events
     */
    @Async
    public void logApiAccess(String endpoint, String method, String username, String ipAddress,
                           String userAgent, int statusCode, long responseTime) {
        try {
            // Log suspicious activities
            if (statusCode == 401 || statusCode == 403) {
                log.warn("Unauthorized access attempt: {} {} by {} from {}", 
                        method, endpoint, username != null ? username : "ANONYMOUS", ipAddress);
                
                logSecurityEvent("UNAUTHORIZED_ACCESS", username, ipAddress,
                    String.format("Unauthorized access to %s %s", method, endpoint), "MEDIUM");
            }
            
            // Log slow requests
            if (responseTime > 5000) {
                log.warn("Slow API response: {} {} took {}ms", method, endpoint, responseTime);
            }
        } catch (Exception e) {
            log.error("Error logging API access: {}", e.getMessage());
        }
    }

    /**
     * Log security events
     */
    @Async
    public void logSecurityEvent(String eventType, String username, String ipAddress, 
                               String details, String severity) {
        try {
            // Log based on severity
            switch (severity.toUpperCase()) {
                case "HIGH" -> log.error("HIGH severity security event: {} - {}", eventType, details);
                case "MEDIUM" -> log.warn("MEDIUM severity security event: {} - {}", eventType, details);
                default -> log.info("Security event: {} - {}", eventType, details);
            }
            
            // Additional alerting logic can be added here
            if ("HIGH".equals(severity)) {
                // In a real application, this could trigger alerts to security team
                log.error("SECURITY ALERT: High severity event requires immediate attention!");
            }
        } catch (Exception e) {
            log.error("Error logging security event: {}", e.getMessage());
        }
    }

    /**
     * Log data access events
     */
    @Async
    public void logDataAccess(String entityType, Long entityId, String operation, 
                            String username, String details) {
        try {
            log.debug("Data access logged: {} {} on {} ID: {} by {}", 
                    operation, entityType, entityId, username);
            
            // Log sensitive data access
            if ("User".equals(entityType) || "Admin".equals(entityType)) {
                log.info("Sensitive data access: {} {} on {} ID: {} by {}", 
                        operation, entityType, entityId, username);
            }
        } catch (Exception e) {
            log.error("Error logging data access: {}", e.getMessage());
        }
    }

    /**
     * Log admin actions
     */
    @Async
    public void logAdminAction(String action, String targetUser, String details, String ipAddress) {
        try {
            String adminUsername = getCurrentUsername();
            
            log.warn("Admin action performed: {} by {} on {} - {}", 
                    action, adminUsername, targetUser, details);
            
            // All admin actions are considered high priority
            logSecurityEvent("ADMIN_ACTION", adminUsername, ipAddress,
                String.format("Action: %s, Target: %s, Details: %s", action, targetUser, details),
                "HIGH");
        } catch (Exception e) {
            log.error("Error logging admin action: {}", e.getMessage());
        }
    }

    /**
     * Get audit statistics for dashboard
     */
    public Map<String, Object> getAuditStatistics() {
        return Map.of(
                "activeFailedAttempts", failedLoginAttempts.size(),
                "totalFailedAttempts", failedLoginAttempts.values().stream()
                        .mapToLong(AtomicLong::get).sum(),
                "lastStatisticsUpdate", LocalDateTime.now()
        );
    }

    /**
     * Clean up old tracking data
     */
    @Async
    public void cleanupOldTrackingData() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        
        lastFailedAttempt.entrySet().removeIf(entry -> 
            entry.getValue().isBefore(cutoff));
        
        // Remove corresponding failed attempts
        failedLoginAttempts.keySet().retainAll(lastFailedAttempt.keySet());
        
        log.info("Cleaned up old security tracking data");
    }

    /**
     * Check if IP has suspicious activity
     */
    public boolean hasSuspiciousActivity(String ipAddress) {
        return failedLoginAttempts.entrySet().stream()
                .anyMatch(entry -> entry.getKey().startsWith(ipAddress + ":") && 
                         entry.getValue().get() >= 3);
    }

    /**
     * Get current authenticated username
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "SYSTEM";
    }

    /**
     * Extract client IP from request
     */
    public String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
