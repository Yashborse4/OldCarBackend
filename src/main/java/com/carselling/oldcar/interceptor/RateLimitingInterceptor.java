package com.carselling.oldcar.interceptor;

import com.carselling.oldcar.annotation.RateLimit;
import com.carselling.oldcar.config.RateLimitingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting interceptor for API endpoints
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingInterceptor implements HandlerInterceptor {

    private final RateLimitingConfig rateLimitingConfig;
    private final RateLimitingConfig.RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!rateLimitingConfig.isRateLimitingEnabled()) {
            log.debug("Rate limiting is disabled");
            return true;
        }

        String clientId = getClientIdentifier(request);
        String bucketKey = "rate_limit_" + clientId;
        String requestPath = request.getRequestURI();

        try {
            // Check for @RateLimit annotation on the handler method
            RateLimit rateLimitAnnotation = getRateLimitAnnotation(handler);

            // Generate bucket key based on annotation presence to allow separate buckets
            // for sensitive endpoints
            if (rateLimitAnnotation != null) {
                bucketKey += "_" + requestPath; // Specific bucket for this endpoint
            }

            // Check if this request is allowed using our custom rate limiting service
            boolean isAllowed;

            if (rateLimitAnnotation != null) {
                isAllowed = rateLimitService.isAllowed(bucketKey, rateLimitAnnotation.capacity(),
                        rateLimitAnnotation.refill(), rateLimitAnnotation.refillPeriod());
            } else {
                isAllowed = rateLimitService.isAllowed(bucketKey);
            }

            if (isAllowed) {
                // Request allowed - add rate limit headers
                long remainingTokens = rateLimitService.getRemainingTokens(bucketKey);
                response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remainingTokens));
                response.setHeader("X-Rate-Limit-Reset",
                        String.valueOf(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1)));

                log.debug("Rate limit check passed for client: {} on path: {} (remaining: {})",
                        clientId, requestPath, remainingTokens);
                return true;
            } else {
                // Rate limit exceeded
                long retryAfterSeconds = 60; // Default 1 minute retry

                // Set rate limit headers
                response.setHeader("X-Rate-Limit-Retry-After", String.valueOf(retryAfterSeconds));
                response.setHeader("X-Rate-Limit-Remaining", "0");

                // Send error response
                sendRateLimitExceededResponse(response, clientId, retryAfterSeconds);

                log.warn("Rate limit exceeded for client: {} on path: {} (retry after: {}s)",
                        clientId, requestPath, retryAfterSeconds);
                return false;
            }

        } catch (Exception e) {
            log.error("Error checking rate limit for client: {} on path: {}", clientId, requestPath, e);
            // Fail open - allow request to proceed if rate limiting encounters an error
            // In production, you might want to fail closed for security
            return true;
        }
    }

    /**
     * Send rate limit exceeded response with proper JSON format
     */
    private void sendRateLimitExceededResponse(HttpServletResponse response, String clientId, long retryAfterSeconds) {
        try {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Rate limit exceeded");
            errorResponse.put("message", "Too many requests. Please try again later.");
            errorResponse.put("retryAfter", retryAfterSeconds);
            errorResponse.put("timestamp", System.currentTimeMillis());

            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            response.getWriter().write(jsonResponse);
            response.getWriter().flush();

        } catch (IOException e) {
            log.error("Failed to write rate limit exceeded response for client: {}", clientId, e);
            // Fallback to simple text response
            try {
                response.getWriter().write("Rate limit exceeded. Please try again later.");
            } catch (IOException fallbackException) {
                log.error("Failed to write fallback response", fallbackException);
            }
        }
    }

    /**
     * Helper to extract RateLimit annotation from handler method
     */
    private RateLimit getRateLimitAnnotation(Object handler) {
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            return handlerMethod.getMethodAnnotation(RateLimit.class);
        }
        return null;
    }

    /**
     * Extract client identifier from request headers and remote address
     * Prioritizes real client IP over proxy addresses
     */
    private String getClientIdentifier(HttpServletRequest request) {
        // 1. Check for Authenticated User
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();

            if (auth != null && auth.isAuthenticated() &&
                    !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {

                Object principal = auth.getPrincipal();
                if (principal instanceof com.carselling.oldcar.security.UserPrincipal) {
                    Long userId = ((com.carselling.oldcar.security.UserPrincipal) principal).getId();
                    return "user:" + userId;
                } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                    return "user:"
                            + ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
                } else {
                    return "user:" + auth.getName();
                }
            }
        } catch (Exception e) {
            // Ignore security context errors, fall back to IP
            log.trace("Could not retrieve user from security context", e);
        }

        // 2. Fall back to IP-based identification
        // Check for X-Forwarded-For header (common with proxies/load balancers)
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.trim().isEmpty()) {
            // Take the first IP in the chain (original client)
            String clientIp = forwardedFor.split(",")[0].trim();
            if (!clientIp.isEmpty() && !"unknown".equalsIgnoreCase(clientIp)) {
                return "ip:" + clientIp;
            }
        }

        // Check for X-Real-IP header (common with Nginx)
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty() && !"unknown".equalsIgnoreCase(realIp)) {
            return "ip:" + realIp;
        }

        // Check for Cloudflare specific header
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.trim().isEmpty()) {
            return "ip:" + cfConnectingIp;
        }

        // Fall back to remote address
        String remoteAddr = request.getRemoteAddr();
        return "ip:" + (remoteAddr != null ? remoteAddr : "unknown");
    }
}
