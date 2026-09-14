package com.helpdesk.queue.dto;

import com.helpdesk.ticket.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

/**
 * F4 - Ticket Resolution & Queue Engine (Weerabaddana)
 *
 * Request body for PUT /api/queue/{id}/status. Only ever moves a ticket
 * OPEN -> IN_PROGRESS; QueueService.updateStatus rejects a target of
 * RESOLVED here (that happens through ResolutionService instead, together
 * with the resolution text) and rejects any other jump as out of order.
 */
public class TicketStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private TicketStatus status;

    public TicketStatus getStatus() {
        return status;
    }
    public void setStatus(TicketStatus status) {
        this.status = status;
    }
}
