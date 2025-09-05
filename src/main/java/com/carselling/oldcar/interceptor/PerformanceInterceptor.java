package com.carselling.oldcar.interceptor;

import com.carselling.oldcar.service.PerformanceMonitoringService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor to monitor performance of API calls
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceInterceptor implements HandlerInterceptor {

    private final PerformanceMonitoringService performanceService;
    private static final String START_TIME_ATTRIBUTE = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
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
            
            // Record the performance metrics
            performanceService.recordApiCall(endpoint, executionTime, success);
            
            // Log slow requests (> 2 seconds)
            if (executionTime > 2000) {
                log.warn("Slow API call detected: {} took {} ms, status: {}", 
                        endpoint, executionTime, response.getStatus());
            }
            
            // Log errors
            if (!success) {
                log.error("API call failed: {} took {} ms, status: {}, exception: {}", 
                        endpoint, executionTime, response.getStatus(), 
                        ex != null ? ex.getMessage() : "None");
            }
        }
    }
}
