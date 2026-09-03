package com.helpdesk.common.exception;

/**
 * Thrown when a request would create a record that violates a uniqueness rule -
 * e.g. registering with an email or Student ID that's already taken.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
