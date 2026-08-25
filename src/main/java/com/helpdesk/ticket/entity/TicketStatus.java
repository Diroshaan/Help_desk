package com.helpdesk.ticket.entity;

/**
 * F2 - Advanced Ticket Request Engine
 *
 * Lifecycle states a ticket moves through. Transitions (OPEN -> IN_PROGRESS ->
 * RESOLVED) are driven by the F4 Queue Engine; a student may only edit or
 * withdraw a ticket while it is still OPEN.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED,
    WITHDRAWN
}
