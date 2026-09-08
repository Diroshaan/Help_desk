package com.helpdesk.common.exception;

/**
 * Thrown when a request names a record that does not exist - e.g. a
 * PUT /api/students/{id} whose id matches no row.
 *
 * GlobalExceptionHandler maps this to 404 Not Found. That mapping is the whole
 * reason a dedicated exception type exists instead of an IllegalArgumentException:
 * the exception TYPE is what carries the meaning "this thing is missing" from the
 * service layer, where the lookup failed, out to the HTTP layer, where a status
 * code has to be chosen. A service has no business knowing about status codes,
 * and a controller has no business re-deriving why a lookup came back empty, so
 * the type is what travels between them.
 *
 * It extends RuntimeException rather than Exception on purpose. A checked
 * exception would force every caller in between to declare or catch it, which
 * adds noise to methods that cannot do anything useful about it anyway - the
 * only sensible handler is the global one.
 *
 * (Historical note: this file previously carried a copy of
 * GlobalExceptionHandler's class comment, describing @RestControllerAdvice.
 * It was pasted here by mistake and described a different class entirely.)
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
