package com.helpdesk.common.exception;

/** Thrown when a unique value (email, student ID...) is already taken. Mapped to 409. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
