package com.helpdesk.common.exception;

/**
 * Thrown when a requested record doesn't exist. GlobalExceptionHandler maps it to 404, so
 * services don't need to know about HTTP status codes.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
