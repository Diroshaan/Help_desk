package com.helpdesk.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

/**
 @RestControllerAdvice applies these handlers globally, to every
 @RestController in the app - it does not need to be wired into each controller individually.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateResource(DuplicateResourceException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * The database refused a write because it would have broken a constraint.
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
     * the correct answer is the same 409 the pre-check would have given.
     *
     *
     * WHY THIS NOW INSPECTS THE ERROR CODE INSTEAD OF ASSUMING "DUPLICATE"
     * --------------------------------------------------------------------
     * The earlier version answered EVERY DataIntegrityViolationException with
     * "That value is already in use by another account." That was wrong often
     * enough to be worth fixing properly, and it cost a full afternoon once:
     * the avatar upload was failing because profile_picture had been created as
     * TINYBLOB (255 bytes), the database was correctly reporting
     *
     *     SQL Error [1406] [22001]: Data too long for column 'profile_picture'
     *
     * and this class was telling the student their photograph was a duplicate
     * email address. Every minute spent looking at the email field was spent
     * because of this message.
     *
     * A constraint violation is not one thing. Uniqueness, length, nullability
     * and foreign keys all arrive here as the same Spring exception, and only
     * the underlying SQLException distinguishes them. So it is unwrapped and
     * read.
     *
     * SQLState is checked before the vendor error code because SQLState is the
     * standardised part: 22001 means "string data, right truncation" on MySQL
     * and on H2 alike, and this project runs both (H2 in development, MySQL in
     * the hosted database). The vendor codes are kept as a second pass for the
     * cases where MySQL is more specific than the standard class.
     *
     * The message still never includes ex.getMessage(). That text carries the
     * constraint name and usually the failing SQL - schema detail that tells an
     * attacker how the database is laid out and means nothing to a student
     * looking at a form. Instead the real exception goes to the SERVER LOG at
     * WARN, where a developer can read it and a student cannot. That is the
     * half that was missing: the information existed, it just never reached
     * anybody who could act on it.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {

        SQLException cause = findSqlException(ex);
        String sqlState = (cause == null) ? null : cause.getSQLState();
        int vendorCode = (cause == null) ? 0 : cause.getErrorCode();

        // Logged in full, once, where it is useful. Without this line the only
        // account of what actually failed is the sanitised sentence below.
        log.warn("Data integrity violation (SQLState={}, vendorCode={}): {}",
                sqlState, vendorCode, ex.getMostSpecificCause().getMessage());

        // Value too long for its column. 400, not 409 - nothing is in conflict,
        // the input is simply bigger than the field allows.
        if ("22001".equals(sqlState) || vendorCode == 1406) {
            return buildResponse(HttpStatus.BAD_REQUEST,
                    "One of the values you entered is too long for the field it was entered in.");
        }

        // A required value was missing. Normally bean validation catches this
        // first and returns a field-by-field 400; reaching here means a column
        // is NOT NULL without a matching @NotNull on the entity.
        if ("23502".equals(sqlState) || vendorCode == 1048) {
            return buildResponse(HttpStatus.BAD_REQUEST,
                    "A required value was missing.");
        }

        // Foreign key: the row refers to something that does not exist, or
        // something still refers to the row being removed.
        if ("23503".equals(sqlState) || vendorCode == 1451 || vendorCode == 1452) {
            return buildResponse(HttpStatus.CONFLICT,
                    "That record is linked to others and cannot be saved or removed as requested.");
        }

        // Uniqueness. 1062 is MySQL's duplicate-entry code; 23505 is H2's
        // SQLState for the same thing. 23000 is the broad "integrity constraint
        // violation" class MySQL reports for duplicates too, so it is checked
        // last of the 23xxx family - the more specific cases above have already
        // had their chance.
        if (vendorCode == 1062 || "23505".equals(sqlState) || "23000".equals(sqlState)) {
            return buildResponse(HttpStatus.CONFLICT,
                    "That value is already in use by another account. Please check and try again.");
        }

        // Something else broke a constraint. Say so plainly rather than
        // guessing, which is exactly the mistake this method was rewritten to
        // stop making. The server log above has the detail.
        return buildResponse(HttpStatus.CONFLICT,
                "That change could not be saved because it conflicts with data already stored.");
    }

    /**
     * Walk down the cause chain to the JDBC exception underneath.
     *
     * Spring wraps the driver's SQLException in a DataAccessException, usually
     * via a Hibernate ConstraintViolationException, so the useful codes are two
     * or three levels down. The loop is bounded by the chain terminating at
     * null; a self-referential cause would be a driver bug, and getCause()
     * returning the same object is guarded against so this cannot spin.
     */
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

    /**
     * The uploaded file was larger than spring.servlet.multipart.max-file-size.
     *
     * Spring rejects it inside the multipart parser, before any controller
     * method runs, so the size check in StudentService.updateAvatar never gets
     * the chance to produce its friendlier message. Without this handler that
     * arrives as a 500, which tells the student their upload broke the server
     * rather than that their file is too big.
     *
     * 413 rather than 400: there is a status code that means exactly this, and
     * using it means a client can distinguish "too large" from "malformed"
     * without reading the message.
     */
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

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * A validation rule on an ENTITY failed while Hibernate was saving it.
     *
     * This is a different moment from MethodArgumentNotValidException above.
     * That one fires at the controller, on the request DTO, before any work is
     * done. This one fires inside a save, when Hibernate re-validates the entity
     * it is about to write. It is the last line of defence: it catches any path
     * that did not come through a validated DTO - a service that builds an
     * entity itself, a seeder, another feature's endpoint.
     *
     * WHY IT NEEDS ITS OWN HANDLER
     * ----------------------------
     * ConstraintViolationException is a subclass of ValidationException, so
     * before this method existed it fell through to handleValidation below,
     * which returned ex.getMessage(). For this exception that message is
     * Hibernate's own diagnostic text:
     *
     *   "Validation failed for classes [com.helpdesk.profile.entity.Student]
     *    during update time for groups [jakarta.validation.groups.Default, ]
     *    List of constraint violations:[ ConstraintViolationImpl{..."
     *
     * Found by testing: a student saving a 130-character name saw exactly that
     * in the red box on the profile page. It exposes internal class and package
     * names, and it buries the one useful sentence - "Full name must be 120
     * characters or fewer" - in the middle of a stack of framework detail.
     *
     * The violations themselves already carry the messages written on the
     * entity's annotations, so this reads those and joins them, the same way
     * handleValidationErrors does for DTO failures. Spring chooses the most
     * specific matching @ExceptionHandler, so this one wins over
     * handleValidation for this subclass and the general handler keeps covering
     * everything else.
     *
     * The DTOs are still the first line (see RegistrationRequest for why the
     * length limits are repeated there); this is what makes a gap in any DTO,
     * in any feature, fail with a readable sentence rather than a class name.
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
