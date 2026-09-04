package com.helpdesk.common.exception;

/**
 * Thrown when a request would create a record that violates a uniqueness rule -
 * e.g. registering with an email or Student ID that's already taken.
 *
 * This is a distinct type from the generic IllegalArgumentException on purpose:
 * services also throw IllegalArgumentException for things like "Student not
 * found" (a 404-shaped problem), and lumping both cases under one exception
 * type would force GlobalExceptionHandler to guess at the right HTTP status
 * from the message text. A dedicated exception lets the handler map this
 * specific situation to 409 Conflict without touching unrelated error paths.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
