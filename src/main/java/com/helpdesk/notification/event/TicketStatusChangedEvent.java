package com.helpdesk.notification.event;

import com.helpdesk.ticket.entity.TicketStatus;

/**
 * OBSERVER PATTERN: the event (the "something happened" message).
 *
 * Published by QueueService whenever an officer moves a ticket to a new
 * status. QueueService doesn't know or care who's listening. Today it's
 * TicketStatusNotifier; tomorrow it could also be a status-history writer
 * (issue #45) or an analytics counter, and QueueService wouldn't change.
 *
 * It carries plain values, not the Ticket entity, because the listeners run
 * after the transaction has committed and the entity is detached by then.
 */
public record TicketStatusChangedEvent(Long ticketId,
                                       Long studentId,
                                       String subject,
                                       TicketStatus fromStatus,
                                       TicketStatus toStatus) {
}
