package com.helpdesk.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Turns exceptions from any controller into a consistent JSON error response. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateResource(DuplicateResourceException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * The database rejected a write. Service pre-checks can be raced, so the constraint is
     * the real guarantee. We read the SQL error code to pick the right message (too long,
     * missing, foreign key, duplicate) and log the detail instead of showing it to the user.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {

        SQLException cause = findSqlException(ex);
        String sqlState = (cause == null) ? null : cause.getSQLState();
        int vendorCode = (cause == null) ? 0 : cause.getErrorCode();

        log.warn("Data integrity violation (SQLState={}, vendorCode={}): {}",
                sqlState, vendorCode, ex.getMostSpecificCause().getMessage());

        // too long for the column - bad input, not a conflict
        if ("22001".equals(sqlState) || vendorCode == 1406) {
            return buildResponse(HttpStatus.BAD_REQUEST,
                    "One of the values you entered is too long for the field it was entered in.");
        }

        // NOT NULL column with no matching @NotNull on the entity
        if ("23502".equals(sqlState) || vendorCode == 1048) {
            return buildResponse(HttpStatus.BAD_REQUEST,
                    "A required value was missing.");
        }

        if ("23503".equals(sqlState) || vendorCode == 1451 || vendorCode == 1452) {
            return buildResponse(HttpStatus.CONFLICT,
                    "That record is linked to others and cannot be saved or removed as requested.");
        }

        // 1062 = MySQL duplicate, 23505 = H2. 23000 is MySQL's broad class, so it's checked last.
        if (vendorCode == 1062 || "23505".equals(sqlState) || "23000".equals(sqlState)) {
            return buildResponse(HttpStatus.CONFLICT,
                    "That value is already in use by another account. Please check and try again.");
        }

        return buildResponse(HttpStatus.CONFLICT,
                "That change could not be saved because it conflicts with data already stored.");
    }

    // The SQLException is usually a few levels down the cause chain.
    private SQLException findSqlException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            Throwable next = current.getCause();
            if (next == current) {
                return null;
            }
            current = next;
        }
        return null;
    }

    // Spring rejects oversized uploads before the controller runs, so without this it's a 500.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE,
                "That file is too large to upload.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return buildResponse(HttpStatus.BAD_REQUEST, message);

    }

    // Optimistic locking (@Version): someone saved the record first, so return 409 and let the
    // user reload instead of overwriting their change.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.info("Concurrent update rejected: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT,
                "Someone else changed this while you were editing it. Reload the page and try again.");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Entity validation that failed during save (paths that skipped a validated DTO).
     * Returns the annotation messages instead of Hibernate's raw text, which shows class names.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .distinct()
                .sorted()
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "One of the values you entered is not valid.";
        }
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(jakarta.validation.ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(jakarta.validation.ValidationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

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
