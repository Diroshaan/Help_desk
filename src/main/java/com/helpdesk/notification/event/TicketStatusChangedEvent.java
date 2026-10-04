package com.helpdesk.notification.event;

import com.helpdesk.ticket.entity.TicketStatus;

/**
 * Observer event: a ticket moved to a new status. QueueService publishes it without
 * knowing who listens. Carries plain values because listeners run after commit, when
 * the entity is detached.
 * changedByOfficerId is null when no officer made the change (e.g. the student withdrew).
 */
public record TicketStatusChangedEvent(Long ticketId,
                                       Long studentId,
                                       String subject,
                                       TicketStatus fromStatus,
                                       TicketStatus toStatus,
                                       Long changedByOfficerId) {

    /** Without an officer id; the officer is recorded as unknown. */
    public TicketStatusChangedEvent(Long ticketId,
                                    Long studentId,
                                    String subject,
                                    TicketStatus fromStatus,
                                    TicketStatus toStatus) {
        this(ticketId, studentId, subject, fromStatus, toStatus, null);
    }
}
