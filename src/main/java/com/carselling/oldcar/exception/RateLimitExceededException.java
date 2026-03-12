package com.carselling.oldcar.exception;

/**
 * Exception thrown when rate limit is exceeded
 */
@lombok.Getter
public class RateLimitExceededException extends RuntimeException {
    
    private final Long remainingSeconds;
    
    public RateLimitExceededException(String message) {
        this(message, null);
    }
    
    public RateLimitExceededException(String message, Long remainingSeconds) {
        super(message);
        this.remainingSeconds = remainingSeconds;
    }
    
    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
        this.remainingSeconds = null;
    }
}
