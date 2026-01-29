package com.carselling.oldcar.filter;

import com.carselling.oldcar.dto.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Idempotency Filter
 * Prevents duplicate processing of state-changing requests (POST, PUT, PATCH)
 * using an Idempotency-Key header.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private final ObjectMapper objectMapper;

    // In-memory cache for idempotency keys (Key -> CachedResponse)
    // In production, this should be backed by Redis
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();

    // Cleanup scheduler
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor();

    public IdempotencyFilter() {
        this.objectMapper = new ObjectMapper();
        // Schedule cleanup every minute to remove expired keys
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredKeys, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();
        // Only apply to state-changing methods
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method) && !"PATCH".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String idempotencyKey = request.getHeader(IDEMPOTENCY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check if key exists in cache
        CachedResponse cachedResponse = cache.get(idempotencyKey);
        if (cachedResponse != null) {
            log.info("Returning cached response for Idempotency-Key: {}", idempotencyKey);
            returnCachedResponse(response, cachedResponse);
            return;
        }

        // Wrap response to capture content
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(request, responseWrapper);
        } finally {
            // Cache successful responses (2xx) or specific error cases if needed
            // Only cache if processing actually happened (not 500s usually, but strict
            // idempotency might dictate otherwise)
            // For now, caching 200-299 responses
            int status = responseWrapper.getStatus();
            if (status >= 200 && status < 300) {
                byte[] responseBody = responseWrapper.getContentAsByteArray();
                CachedResponse newCache = new CachedResponse(status, responseBody,
                        System.currentTimeMillis() + TimeUnit.HOURS.toMillis(24)); // 24h TTL
                cache.put(idempotencyKey, newCache);
            }
            // IMPORTANT: Copy content back to original response
            responseWrapper.copyBodyToResponse();
        }
    }

    private void returnCachedResponse(HttpServletResponse response, CachedResponse cachedResponse) throws IOException {
        response.setStatus(cachedResponse.status);
        response.setContentType("application/json"); // Assuming JSON for now
        response.getOutputStream().write(cachedResponse.body);
    }

    private void cleanupExpiredKeys() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().expiry < now);
    }

    private record CachedResponse(int status, byte[] body, long expiry) {
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip for public endpoints or endpoints where idempotency might be
        // tricky/unwanted
        return path.startsWith("/api/auth/") || path.startsWith("/ws");
    }
}
