package com.carselling.oldcar.interceptor;

import com.carselling.oldcar.annotation.RateLimit;
import com.carselling.oldcar.config.RateLimitingConfig;
import com.carselling.oldcar.security.UserPrincipal;
import com.carselling.oldcar.service.SystemHealthMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Rate limiting interceptor for API endpoints.
 * Implements Netflix-style adaptive load shedding and prioritization.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingInterceptor implements HandlerInterceptor {

    private final RateLimitingConfig rateLimitingConfig;
    private final RateLimitingConfig.RoleLimitsConfig roleLimitsConfig;
    private final RateLimitingConfig.RateLimitService rateLimitService;
    private final SystemHealthMonitor systemHealthMonitor;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!rateLimitingConfig.isRateLimitingEnabled()) {
            log.debug("Rate limiting is disabled");
            return true;
        }

        String clientId = getClientIdentifier(request);
        String requestPath = request.getRequestURI();
        double healthScore = systemHealthMonitor.getHealthScore();

        try {
            // Check for @RateLimit annotation on the handler method
            RateLimit rateLimitAnnotation = getRateLimitAnnotation(handler);
            
            // 1. Determine priority
            RateLimitingConfig.RateLimitPriority priority = RateLimitingConfig.RateLimitPriority.NORMAL;
            String priorityStr = "NORMAL";
            if (rateLimitAnnotation != null) {
                priorityStr = rateLimitAnnotation.priority();
                try {
                    priority = RateLimitingConfig.RateLimitPriority.valueOf(priorityStr);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid priority '{}' on endpoint {}, defaulting to NORMAL", 
                            priorityStr, requestPath);
                }
            }

            // 2. GLOBAL LOAD SHEDDING (Netflix Strategy)
            // If system is in critical load (health < 0.3), block everything except CRITICAL
            if (healthScore < 0.3 && priority != RateLimitingConfig.RateLimitPriority.CRITICAL) {
                log.warn("CRITICAL LOAD SHEDDING: Rejecting {} priority request on {} (Health: {})", 
                        priority, requestPath, healthScore);
                sendRateLimitExceededResponse(response, clientId, 30, "System under heavy load. Please try again later.");
                return false;
            }

            // 3. Generate bucket key
            String bucketKey = "rate_limit_" + clientId;
            if (rateLimitAnnotation != null) {
                bucketKey += "_" + requestPath; // Specific bucket for this endpoint
            }

            // 4. Extract role for default limits
            String role = com.carselling.oldcar.model.Role.ANONYMOUS.name();
            if (isAuthenticated()) {
                role = getUserRole();
            }

            // 5. Check Rate Limit with Adaptive Logic
            RateLimitingConfig.RateLimitService.CheckResult result;
            if (rateLimitAnnotation != null) {
                result = rateLimitService.allow(bucketKey, rateLimitAnnotation.capacity(),
                        rateLimitAnnotation.refill(), rateLimitAnnotation.refillPeriod(), 
                        priority, healthScore);
            } else {
                // For default role-based limits, we still apply adaptive health scaling
                int[] roleLimits = getRoleLimits(role);
                result = rateLimitService.allow(bucketKey, roleLimits[0], roleLimits[1], 1,
                        priority, healthScore);
            }

            // 6. Set Headers (RFC Compliant)
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(result.remaining()));
            response.setHeader("X-Rate-Limit-Reset", String.valueOf(result.resetTimeMs() / 1000));
            
            if (result.allowed()) {
                log.debug("Rate limit passed: {} on {} (remaining: {})", clientId, requestPath, result.remaining());
                return true;
            } else {
                long retryAfterSeconds = (result.resetTimeMs() - System.currentTimeMillis()) / 1000;
                if (retryAfterSeconds <= 0) retryAfterSeconds = 1;

                response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                sendRateLimitExceededResponse(response, clientId, retryAfterSeconds, "Too many requests. Please try again later.");
                
                log.warn("Rate limit exceeded: {} on {} (retry after: {}s)", clientId, requestPath, retryAfterSeconds);
                return false;
            }

        } catch (Exception e) {
            log.error("Rate limit check error: {} on {}", clientId, requestPath, e);
            return true; // Fail open
        }
    }

    /**
     * Send rate limit exceeded response with proper JSON format
     */
    private void sendRateLimitExceededResponse(HttpServletResponse response, String clientId, long retryAfterSeconds, String message) {
        try {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Rate limit exceeded");
            errorResponse.put("message", message);
            errorResponse.put("retryAfter", retryAfterSeconds);
            errorResponse.put("timestamp", System.currentTimeMillis());

            String jsonResponse = objectMapper.writeValueAsString(errorResponse);
            response.getWriter().write(jsonResponse);
            response.getWriter().flush();

        } catch (IOException e) {
            log.error("Failed to write rate limit exceeded response for client: {}", clientId, e);
        }
    }

    private int[] getRoleLimits(String role) {
        if ("ADMIN".equals(role)) return new int[]{roleLimitsConfig.adminCap(), roleLimitsConfig.adminRefill()};
        if ("DEALER".equals(role)) return new int[]{roleLimitsConfig.dealerCap(), roleLimitsConfig.dealerRefill()};
        if ("USER".equals(role)) return new int[]{roleLimitsConfig.userCap(), roleLimitsConfig.userRefill()};
        return new int[]{roleLimitsConfig.anonCap(), roleLimitsConfig.anonRefill()};
    }

    private RateLimit getRateLimitAnnotation(Object handler) {
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            return handlerMethod.getMethodAnnotation(RateLimit.class);
        }
        return null;
    }

    private String getClientIdentifier(HttpServletRequest request) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                Object principal = auth.getPrincipal();
                if (principal instanceof UserPrincipal) {
                    return "user:" + ((UserPrincipal) principal).getId();
                } else if (principal instanceof UserDetails) {
                    return "user:" + ((UserDetails) principal).getUsername();
                }
                return "user:" + auth.getName();
            }
        } catch (Exception e) {
            log.trace("Could not retrieve user from security context", e);
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.trim().isEmpty()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null) return "ip:" + realIp;
        
        return "ip:" + request.getRemoteAddr();
    }

    private boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    private String getUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return com.carselling.oldcar.model.Role.ANONYMOUS.name();
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal) {
            return ((UserPrincipal) principal).getRole().name();
        }
        return auth.getAuthorities().stream()
                .filter(a -> a.getAuthority().startsWith("ROLE_"))
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .findFirst()
                .orElse(com.carselling.oldcar.model.Role.USER.name());
    }
}
