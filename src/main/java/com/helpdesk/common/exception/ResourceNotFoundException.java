package com.helpdesk.common.exception;

/**
 * Thrown when a request refers to a record that does not exist - e.g. a
 * bookmark folder id that has been deleted, or never belonged to the
 * requesting student in the first place.
 *
 * This is a distinct type from DuplicateResourceException on purpose: the two
 * map to different HTTP statuses (404 vs 409), so GlobalExceptionHandler needs
 * a dedicated type for each rather than inferring the right status from a
 * message string.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
