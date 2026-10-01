package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.entity.TicketStatus;

import java.time.LocalDateTime;

/**
 * One row of a ticket's status-change timeline (contract C3, #45).
 *
 * changedBy is a display name ("Student", an officer's full name, or
 * "Unknown") - never a bare id, which would mean nothing to the student
 * reading their own timeline.
 */
public record TicketStatusChangeResponse(int sequenceNo,
                                         TicketStatus fromStatus,
                                         TicketStatus toStatus,
                                         String changedBy,
                                         LocalDateTime changedAt) {
}
