package com.carselling.oldcar.security.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT Authentication Entry Point for handling unauthorized access
 * This class handles authentication failures and returns structured error responses
 */
@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, 
                        HttpServletResponse response,
                        AuthenticationException authException) throws IOException {
        
        log.error("Unauthorized access attempt: {} - {}", 
                request.getRequestURI(), authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Map<String, Object> errorResponse = createErrorResponse(request, authException);
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(response.getOutputStream(), errorResponse);
    }

    private Map<String, Object> createErrorResponse(HttpServletRequest request, 
                                                   AuthenticationException authException) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        errorResponse.put("error", "Unauthorized");
        errorResponse.put("message", determineErrorMessage(authException));
        errorResponse.put("path", request.getRequestURI());
        errorResponse.put("method", request.getMethod());
        
        // Additional context for debugging (remove in production)
        if (log.isDebugEnabled()) {
            errorResponse.put("details", authException.getMessage());
        }

        return errorResponse;
    }

    private String determineErrorMessage(AuthenticationException authException) {
        String message = authException.getMessage();
        
        // Provide user-friendly error messages
        if (message != null) {
            if (message.contains("JWT expired")) {
                return "Your session has expired. Please login again.";
            } else if (message.contains("JWT signature")) {
                return "Invalid authentication token. Please login again.";
            } else if (message.contains("JWT token")) {
                return "Invalid authentication token format. Please login again.";
            }
        }
        
        return "Authentication required. Please provide a valid authentication token.";
    }
}
