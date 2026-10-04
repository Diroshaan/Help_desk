package com.helpdesk.ticket.service;

import com.helpdesk.notification.event.TicketStatusChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer: listens for TicketStatusChangedEvent and records the change in the
 * ticket's timeline. A plain @EventListener so it runs inside the officer's
 * transaction - the status change and its history row commit or roll back together.
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
