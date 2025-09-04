package com.carselling.oldcar.exception;

import com.carselling.oldcar.dto.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Comprehensive Global Exception Handler
 * Provides unified error handling across the entire application
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Custom Application Exceptions
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {
        
        log.error("Resource not found: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message(ex.getMessage())
                .details("The requested resource could not be found")
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceAlreadyExistsException(
            ResourceAlreadyExistsException ex, HttpServletRequest request) {
        
        log.error("Resource already exists: {}", ex.getMessage(), ex);
        
        String message = determineConflictMessage(ex.getMessage());
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message(message)
                .details("The resource already exists in the system")
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthenticationFailedException(
            AuthenticationFailedException ex, HttpServletRequest request) {
        
        log.error("Authentication failed: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Authentication failed")
                .details("Invalid username/email or password. Please check your credentials and try again.")
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(InsufficientPermissionException.class)
    public ResponseEntity<ApiResponse<Object>> handleInsufficientPermissionException(
            InsufficientPermissionException ex, HttpServletRequest request) {
        
        log.error("Insufficient permission: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Access denied")
                .details("You don't have sufficient permissions to perform this action")
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ApiResponse<Object>> handleInvalidInputException(
            InvalidInputException ex, HttpServletRequest request) {
        
        log.error("Invalid input: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message(ex.getMessage())
                .details("Invalid input provided")
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // Spring Security Exceptions
    
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Object>> handleBadCredentialsException(
            BadCredentialsException ex, HttpServletRequest request) {
        
        log.error("Bad credentials: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Authentication failed")
                .details("Invalid username/email or password. Please check your credentials and try again.")
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {
        
        log.error("Access denied: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Access denied")
                .details("You don't have permission to access this resource")
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleInsufficientAuthenticationException(
            InsufficientAuthenticationException ex, HttpServletRequest request) {
        
        log.error("Insufficient authentication: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Authentication required")
                .details("Please provide valid authentication credentials")
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        
        log.error("Authentication exception: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Authentication failed")
                .details("Unable to authenticate. Please check your credentials and try again.")
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    // Validation Exceptions
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        log.error("Validation failed: {}", ex.getMessage(), ex);
        
        BindingResult result = ex.getBindingResult();
        Map<String, String> fieldErrors = new HashMap<>();
        
        for (FieldError error : result.getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Validation failed")
                .details("Please check the input data and try again")
                .data(Map.of("fieldErrors", fieldErrors))
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {
        
        log.error("Constraint violation: {}", ex.getMessage(), ex);
        
        Map<String, String> fieldErrors = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                    violation -> violation.getPropertyPath().toString(),
                    ConstraintViolation::getMessage,
                    (existing, replacement) -> existing
                ));
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Validation failed")
                .details("Please check the input data and try again")
                .data(Map.of("fieldErrors", fieldErrors))
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // Database Exceptions
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        
        log.error("Data integrity violation: {}", ex.getMessage(), ex);
        
        String message = "Data integrity violation";
        String details = "The operation violates database constraints";
        
        // Try to provide more specific error messages
        if (ex.getMessage().contains("duplicate") || ex.getMessage().contains("unique")) {
            message = "Duplicate data detected";
            details = "The provided data conflicts with existing records";
        }
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message(message)
                .details(details)
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    // HTTP Exceptions
    
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        
        log.error("Method not supported: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Method not allowed")
                .details(String.format("HTTP method %s is not supported for this endpoint", ex.getMethod()))
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        
        log.error("Media type not supported: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Unsupported media type")
                .details("The request media type is not supported")
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        
        log.error("Message not readable: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Invalid request format")
                .details("The request body is malformed or unreadable")
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        
        log.error("Missing request parameter: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Missing required parameter")
                .details(String.format("Required parameter '%s' is missing", ex.getParameterName()))
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        
        log.error("Argument type mismatch: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Invalid parameter type")
                .details(String.format("Parameter '%s' has invalid type", ex.getName()))
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpServletRequest request) {
        
        log.error("No handler found: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Endpoint not found")
                .details(String.format("No handler found for %s %s", ex.getHttpMethod(), ex.getRequestURL()))
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // Generic Exception Handler (fallback)
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        
        ApiResponse<Object> response = ApiResponse.builder()
                .timestamp(LocalDateTime.now())
                .message("Internal server error")
                .details("An unexpected error occurred. Please try again later.")
                .success(false)
                .build();
        
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Helper Methods
    
    private String determineConflictMessage(String exceptionMessage) {
        if (exceptionMessage.contains("username")) {
            return "Username is already taken. Please choose a different username.";
        } else if (exceptionMessage.contains("email")) {
            return "Email is already registered. Please use a different email or try logging in.";
        }
        return "Resource already exists";
    }
}
