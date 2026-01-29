package com.carselling.oldcar.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor to monitor performance of API calls
 */
@Slf4j
@Component
public class PerformanceInterceptor implements HandlerInterceptor {
    
    private static final String START_TIME_ATTRIBUTE = "startTime";
    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }
        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        MDC.put("correlationId", correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
        if (startTime != null) {
            long executionTime = System.currentTimeMillis() - startTime;
            String endpoint = request.getRequestURI();
            boolean success = (response.getStatus() < 400) && (ex == null);

            log.debug("API call: {} took {} ms, success: {}", endpoint, executionTime, success);

            if (executionTime > 2000) {
                log.warn("Slow API call detected: {} took {} ms, status: {}", 
                        endpoint, executionTime, response.getStatus());
            }

            if (!success) {
                log.error("API call failed: {} took {} ms, status: {}, exception: {}", 
                        endpoint, executionTime, response.getStatus(), 
                        ex != null ? ex.getMessage() : "None");
            }
        }
        MDC.remove("correlationId");
    }
}
