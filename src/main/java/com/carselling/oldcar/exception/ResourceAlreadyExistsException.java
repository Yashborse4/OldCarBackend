package com.carselling.oldcar.exception;

import lombok.Getter;

/**
 * Exception thrown when trying to create a resource that already exists
 */
@Getter
public class ResourceAlreadyExistsException extends RuntimeException {
    
    private final String resourceName;
    private final String fieldName;
    private final Object fieldValue;

    public ResourceAlreadyExistsException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s already exists with %s: %s", resourceName, fieldName, fieldValue));
        this.resourceName = resourceName;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
    }

    public ResourceAlreadyExistsException(String message) {
        super(message);
        this.resourceName = null;
        this.fieldName = null;
        this.fieldValue = null;
    }
}
