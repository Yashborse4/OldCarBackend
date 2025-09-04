package com.carselling.oldcar.exception;

/**
 * Exception thrown when user doesn't have sufficient permissions
 */
public class InsufficientPermissionException extends RuntimeException {

    public InsufficientPermissionException(String message) {
        super(message);
    }

    public InsufficientPermissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
