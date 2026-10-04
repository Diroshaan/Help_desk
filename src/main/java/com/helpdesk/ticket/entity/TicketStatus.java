package com.helpdesk.ticket.entity;

/**
 * Ticket lifecycle: OPEN -> IN_PROGRESS -> RESOLVED, moved along by officers.
 * A student can only edit or withdraw a ticket while it is OPEN.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    WITHDRAWN
}
