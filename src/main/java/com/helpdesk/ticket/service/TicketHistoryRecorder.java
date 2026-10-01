package com.helpdesk.ticket.service;

import com.helpdesk.notification.event.TicketStatusChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * OBSERVER PATTERN: a second observer of TicketStatusChangedEvent (#45).
 *
 * F4's QueueService publishes this event for the student notifier
 * (TicketStatusNotifier); this class listens to the very same event to keep
 * the history. QueueService does not know this class exists, exactly as it
 * does not know TicketStatusNotifier exists - adding a listener here never
 * required a change to the publisher.
 *
 * Plain @EventListener, NOT @TransactionalEventListener: this must run
 * INSIDE the officer's transaction, so the status change and its history
 * row commit or roll back together. TicketStatusNotifier uses
 * AFTER_COMMIT instead, because a notification is a side effect of a
 * change that already happened; a history row is part of the data itself.
 */
@Component
public class TicketHistoryRecorder {

    private final TicketHistoryService historyService;

    public TicketHistoryRecorder(TicketHistoryService historyService) {
        this.historyService = historyService;
    }

    @EventListener
    public void on(TicketStatusChangedEvent event) {
        historyService.record(event.ticketId(), event.fromStatus(), event.toStatus(), event.changedByOfficerId());
    }
}
