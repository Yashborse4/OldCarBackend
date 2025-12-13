package com.carselling.oldcar.exception;

/**
 * Exception thrown when a user attempts to perform an action they are not authorized for
 */
public class UnauthorizedActionException extends RuntimeException {
    
    public UnauthorizedActionException(String message) {
        super(message);
    }
    
    public UnauthorizedActionException(String message, Throwable cause) {
        super(message, cause);
    }
}
