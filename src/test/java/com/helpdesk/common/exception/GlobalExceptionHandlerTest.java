package com.helpdesk.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit tests for GlobalExceptionHandler, one per status mapping. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void optimisticLockFailureBecomes409WithAReloadMessage() {
        // What Hibernate throws when a save finds the row's @Version has moved on.
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("com.helpdesk.ticket.entity.Ticket", 42L);

        ResponseEntity<Map<String, Object>> response = handler.handleOptimisticLock(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("status", 409);
        // Says what to do without leaking the entity class or id.
        assertThat((String) response.getBody().get("message"))
                .contains("Reload")
                .doesNotContain("com.helpdesk")
                .doesNotContain("42");
    }

    @Test
    void notFoundStays404() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleNotFound(new ResourceNotFoundException("Ticket not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", "Ticket not found");
    }
}
