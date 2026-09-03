package com.helpdesk.common.exception;

/**
 Thrown when a request refers to a record that does not exist - e.g. a
 bookmark folder id that has been deleted, or never belonged to the requesting student in the first place.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
