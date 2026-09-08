package com.helpdesk.common.exception;

import org.springframework.dao.DataIntegrityViolationException;
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
 @RestControllerAdvice applies these handlers globally, to every
 @RestController in the app - it does not need to be wired into each controller individually.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateResource(DuplicateResourceException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * The database refused a write because it would have broken a constraint -
     * in this codebase, almost always @Column(unique = true) on
     * Student.studentId or Student.email.
     *
     * Why this handler is needed even though StudentService.register() already
     * checks for duplicates: that check is a query followed by a save, and two
     * requests can both run their query before either reaches its save. Both
     * find nothing, both proceed, and one of them loses. The pre-check makes
     * the collision rare and produces a helpful message in the normal case;
     * the unique constraint is the thing that actually GUARANTEES no duplicate
     * row exists, because the database enforces it and application code cannot
     * be raced past it.
     *
     * Without this handler that guarantee surfaces to the loser of the race as
     * an unhandled exception - a 500, which says "the server is broken" when
     * the correct answer is the same 409 the pre-check would have given. So
     * this is the backstop, not the primary control: two layers answering the
     * same question, one fast and friendly, one slow and certain.
     *
     * The message is deliberately generic and does NOT use ex.getMessage().
     * That text contains the constraint name and usually the SQL statement
     * that failed - internal schema detail that tells an attacker how the
     * database is laid out and means nothing to a student looking at a form.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return buildResponse(HttpStatus.CONFLICT,
                "That value is already in use by another account. Please check and try again.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return buildResponse(HttpStatus.BAD_REQUEST, message);

    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(jakarta.validation.ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(jakarta.validation.ValidationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // A genuine "you sent something this method cannot work with" fault.
    //
    // This handler used to carry the "student not found" case as well:
    // StudentService.updateProfile() and .deactivate() threw
    // IllegalArgumentException for an id that matched no row, so a missing
    // student came back as 400 Bad Request. That was the wrong status. The
    // request was perfectly well formed - it just named something that does
    // not exist, which is the definition of 404. Those two methods now throw
    // ResourceNotFoundException instead, handled above, so this handler is
    // back to covering only what its name says.
    //
    // It is kept because IllegalArgumentException can still reach here from
    // library code and from other packages, and a 400 is a better default for
    // it than an unhandled 500.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
