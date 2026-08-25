package com.helpdesk.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Thrown by services (e.g. StudentService.register()) when a request would
    // violate a uniqueness rule - duplicate email or Student ID at registration.
    // 409 Conflict is the correct status here: the request is well-formed and
    // the server understood it, but it can't be completed because it conflicts
    // with an existing resource's state.
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateResource(DuplicateResourceException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Thrown automatically by Spring when a @Valid @RequestBody argument (e.g.
    // StudentController.register's Student body) fails one or more Bean
    // Validation constraints (@NotBlank, @Email, etc. on the Student entity).
    // Without this handler, a validation failure is also reported as a bare
    // 500 - which hides the fact that the request itself was invalid and gives
    // the caller no indication of which field(s) need fixing.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        // A request can fail multiple constraints at once (e.g. missing email
        // AND missing password) - collect every field's message into one
        // human-readable string rather than surfacing only the first.
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    // Shared response shape for every handler above, so the frontend can rely
    // on a consistent { message, status, timestamp } body no matter which
    // error path was hit.
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
