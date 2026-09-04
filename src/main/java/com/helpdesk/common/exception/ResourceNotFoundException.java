package com.helpdesk.common.exception;

/**
 * Central place to turn exceptions thrown from controllers/services into clean
 * JSON error responses, instead of letting them fall through to Spring Boot's
 * default error page/handler.
 *
 * Why this exists: without a @RestControllerAdvice, ANY exception that
 * escapes a controller (e.g. DuplicateResourceException from
 * StudentService.register()) is treated as an unhandled server fault - Spring
 * Boot's default error handler turns it into a 500 Internal Server Error with
 * a generic body, regardless of whether the actual problem was the client's
 * fault (like reusing an email that's already registered). That's both
 * misleading (500 implies "we broke", not "you sent bad data") and unhelpful
 * to the frontend, which has no clear message to show the user. Each handler
 * below maps one category of exception to the HTTP status that actually
 * describes it, plus a message the frontend can display as-is (register.html
 * reads response.json().message directly).
 *
 * @RestControllerAdvice applies these handlers globally, to every
 * @RestController in the app - it does not need to be wired into each
 * controller individually.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
