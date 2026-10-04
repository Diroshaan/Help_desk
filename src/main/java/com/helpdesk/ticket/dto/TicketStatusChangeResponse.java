package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.entity.TicketStatus;

import java.time.LocalDateTime;

/** One row of a ticket's timeline. changedBy is a display name, not an id. */
public record TicketStatusChangeResponse(int sequenceNo,
                                         TicketStatus fromStatus,
                                         TicketStatus toStatus,
                                         String changedBy,
                                         LocalDateTime changedAt) {
}
