package com.carselling.oldcar.interceptor;

import com.carselling.oldcar.config.RateLimitConfig;
import com.carselling.oldcar.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * Interceptor for rate limiting requests
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitConfig rateLimitConfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientIp = getClientIpAddress(request);
        String endpoint = request.getRequestURI();
        
        // Determine rate limit based on endpoint
        RateLimitRule rule = determineRateLimitRule(endpoint);
        
        if (rule != null) {
            Bucket bucket = rateLimitConfig.resolveBucket(
                clientIp + ":" + rule.getKey(), 
                rule.getCapacity(), 
                rule.getRefillPeriod()
            );
            
            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded for IP: {} on endpoint: {}", clientIp, endpoint);
                response.setStatus(429); // Too Many Requests
                response.setHeader("X-Rate-Limit-Retry-After-Seconds", 
                    String.valueOf(rule.getRefillPeriod().getSeconds()));
                
                throw new RateLimitExceededException("Rate limit exceeded. Please try again later.");
            }
            
            // Add rate limit headers
            long availableTokens = bucket.getAvailableTokens();
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(availableTokens));
            response.setHeader("X-Rate-Limit-Limit", String.valueOf(rule.getCapacity()));
        }
        
        return true;
    }

    private RateLimitRule determineRateLimitRule(String endpoint) {
        if (endpoint.startsWith("/api/auth/login")) {
            return new RateLimitRule("login", 5, Duration.ofMinutes(15));
        } else if (endpoint.startsWith("/api/auth/register")) {
            return new RateLimitRule("register", 3, Duration.ofMinutes(10));
        } else if (endpoint.startsWith("/api/files/upload")) {
            return new RateLimitRule("upload", 10, Duration.ofHours(1));
        } else if (endpoint.startsWith("/api/email")) {
            return new RateLimitRule("email", 5, Duration.ofHours(1));
        } else if (endpoint.startsWith("/api/")) {
            return new RateLimitRule("api", 100, Duration.ofMinutes(1));
        }
        
        return null; // No rate limiting for non-API endpoints
    }

    private String getClientIpAddress(HttpServletRequest request) {
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

    /**
     * Internal class to represent rate limit rules
     */
    private static class RateLimitRule {
        private final String key;
        private final int capacity;
        private final Duration refillPeriod;

        public RateLimitRule(String key, int capacity, Duration refillPeriod) {
            this.key = key;
            this.capacity = capacity;
            this.refillPeriod = refillPeriod;
        }

        public String getKey() { return key; }
        public int getCapacity() { return capacity; }
        public Duration getRefillPeriod() { return refillPeriod; }
    }
}
