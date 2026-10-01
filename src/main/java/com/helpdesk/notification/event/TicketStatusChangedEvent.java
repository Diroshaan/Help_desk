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
 *
 * changedByOfficerId (added for F2 issue #45, the status-change history)
 * ----------------------------------------------------------------------
 * The history has to say WHO made each change, and the officer who acted is
 * known only inside F4's queue code. So the event carries it.
 *
 *   an officer's id  - an officer made the change
 *   null             - no officer did: the change came from the student
 *                      (create, withdraw) or the publisher has not been
 *                      updated to pass the id yet
 *
 * The second constructor below keeps the original five-value form compiling,
 * with null for the officer. That is deliberate: QueueService (F4, Vimansa's
 * file) and the tests publish this event today, and adding a record component
 * would otherwise break every one of those call sites in the same commit. F4
 * switches to the six-value form when it passes the officer id through
 * (see the F4 guide); until then the history records "unknown officer" rather
 * than guessing one.
 */
public record TicketStatusChangedEvent(Long ticketId,
                                       Long studentId,
                                       String subject,
                                       TicketStatus fromStatus,
                                       TicketStatus toStatus,
                                       Long changedByOfficerId) {

    /** The original form, from before the officer id was carried. The officer is recorded as unknown (null). */
    public TicketStatusChangedEvent(Long ticketId,
                                    Long studentId,
                                    String subject,
                                    TicketStatus fromStatus,
                                    TicketStatus toStatus) {
        this(ticketId, studentId, subject, fromStatus, toStatus, null);
    }
}
