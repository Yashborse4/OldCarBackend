package com.carselling.oldcar.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standardized API Response wrapper for consistent response format
 * Provides uniform structure for all API responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {

    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp = LocalDateTime.now();
    
    private String message;
    private String details;
    private T data;
    private java.util.Map<String, String> fieldErrors;
    
    @Builder.Default
    private Boolean success = true;

    // Static factory methods for common responses
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .message(message)
                .data(data)
                .success(true)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, String details, T data) {
        return ApiResponse.<T>builder()
                .message(message)
                .details(details)
                .data(data)
                .success(true)
                .build();
    }

    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .message(message)
                .success(true)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .message(message)
                .success(false)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String details) {
        return ApiResponse.<T>builder()
                .message(message)
                .details(details)
                .success(false)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, String details, T data) {
        return ApiResponse.<T>builder()
                .message(message)
                .details(details)
                .data(data)
                .success(false)
                .build();
    }
}
